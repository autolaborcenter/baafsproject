package com.thermometer

import cn.autolabor.serialport.manager.writeOrReboot
import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mechdancer.SimpleLogger
import org.mechdancer.WatchDog
import org.mechdancer.common.Stamped
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.exceptions.ExceptionMessage.Occurred
import org.mechdancer.exceptions.device.DataTimeoutException
import org.mechdancer.exceptions.device.DeviceNotExistException
import org.mechdancer.exceptions.device.DeviceOfflineException
import org.mechdancer.exceptions.device.ParseTimeoutException
import java.util.concurrent.Executors

/**
 * 温湿度计驱动
 */
internal class TemperX (
    scope: CoroutineScope,
    private val therm: SendChannel<Stamped<Pair<Double, Double>>>,
    private val exceptions: SendChannel<ExceptionMessage>,

    portName: String?,

    connectionTimeout: Long,
    parseTimeout: Long,
    dataTimeout: Long,
    retryInterval: Long,
    // 读取温度周期
    mainInterval: Long,

    private val logger: SimpleLogger?
    ) : CoroutineScope by scope {
        // 协议解析引擎
        private val engine = com.thermometer.engine()
        // 超时异常监控
        private val offlineException = DeviceOfflineException(NAME)
        private val connectionWatchDog = WatchDog(this, connectionTimeout) { exceptions.send(ExceptionMessage.Occurred(offlineException)) }

        private val parseTimeoutException = ParseTimeoutException(NAME, parseTimeout)
        private val parseWatchDog = WatchDog(this, parseTimeout) { exceptions.send(ExceptionMessage.Occurred(parseTimeoutException)) }

        private val dataTimeoutException = DataTimeoutException(NAME, dataTimeout)
        private val dataWatchDog = WatchDog(this, dataTimeout) { exceptions.send(ExceptionMessage.Occurred(dataTimeoutException)) }

        // 常量参数
        private companion object {
            const val NAME = "temperx232"
            const val BUFFER_SIZE = 64
            const val CMD = "ReadTemp"
        }

        init {
            // 打开串口
            val port: SerialPort
            try {
                port = SerialPort.getCommPort(portName)
                port.baudRate = 9600
                port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 100)
                if (!port.openPort()) {
                    throw RuntimeException("${port.systemPortName}: cannot open")
                }
            } catch (e: RuntimeException) {
                throw DeviceNotExistException(NAME, e.message)
            }
            // 单开线程以执行阻塞读写
            launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) {
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val start = System.currentTimeMillis()
                    if (port.writeOrReboot(CMD.toByteArray(Charsets.US_ASCII), retryInterval) {
                            exceptions.send(Occurred(DeviceOfflineException(NAME)))
                        }) {
                        val len = port.readBytes(buffer, BUFFER_SIZE.toLong())
                        if (len > 0) {
                            write(buffer.take(len))
                        }
                    }
                    while ((System.currentTimeMillis() - start) < mainInterval)
                        delay(10)
                }
            }.invokeOnCompletion {
                port.closePort()
                therm.close()
            }
        }

        private fun write(array: List<Byte>) {
            connectionWatchDog.feed()
            launch { exceptions.send(ExceptionMessage.Recovered(offlineException)) }
            engine(array) { pack ->
                when (pack) {
                    is TempPackage.Nothing -> logger?.log("nothing")
                    is TempPackage.Failed  -> logger?.log("failed")
                    is TempPackage.Data    -> {
                        parseWatchDog.feed()
                        launch { exceptions.send(ExceptionMessage.Recovered(parseTimeoutException)) }
                        val (temp, humi) = pack
                        logger?.log("temp = ${temp} [C], humi = ${humi} [%]")
                        launch {
                            therm.send(Stamped(System.currentTimeMillis(), Pair(temp, humi)))
                            dataWatchDog.feed()
                            launch { exceptions.send(ExceptionMessage.Recovered(dataTimeoutException)) }
                        }
                    }
                }
            }
        }
    }