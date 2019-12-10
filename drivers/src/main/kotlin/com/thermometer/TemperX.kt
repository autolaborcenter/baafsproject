package com.thermometer

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
import org.mechdancer.exceptions.device.DataTimeoutException
import org.mechdancer.exceptions.device.DeviceNotExistException
import org.mechdancer.exceptions.device.DeviceOfflineException
import java.util.concurrent.Executors

/**
 * 温湿度计驱动
 */
internal class TemperX (
    scope: CoroutineScope,
    private val thermometer: SendChannel<Stamped<Pair<Double, Double>>>,
    private val exceptions: SendChannel<ExceptionMessage>,
    portName: String?,      // 串口号
    dataTimeout: Long,      // 数据超时时间
    private val retryInterval: Long,    // 串口重试间隔
    mainInterval: Long,     // 读取温度周期
    private val logger: SimpleLogger?
    ) : CoroutineScope by scope {
        // 协议解析引擎
        private val engine = engine()
        // 超时异常监控
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
                val readCmd = CMD.toByteArray(Charsets.US_ASCII)
                while (true) {
                    val start = System.currentTimeMillis()
                    if (port.writeOrReboot(readCmd) ) {
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
                thermometer.close()
            }
        }

        // 写重连阈值(超过此次数写失败则串口重连)
        private val WRITE_RECON_CNT = 5
        // 写计数
        private var writeFailCnt = 0
        // 从串口读取，并在超时时自动重启串口
        private suspend fun SerialPort.writeOrReboot(buffer: ByteArray): Boolean {
            // 反复尝试写指令
            while (true) {
                // 在单线程上打开串口并写指令
                when (val actual = takeIf { it.isOpen || it.openPort() }?.writeBytes(buffer, buffer.size.toLong())) {
                    buffer.size -> {
                        writeFailCnt = 0
                        return true
                    }
                    else     -> {
                        writeFailCnt++
                        if (writeFailCnt >= WRITE_RECON_CNT)
                            exceptions.send(ExceptionMessage.Occurred(DeviceOfflineException(NAME)))
                    }
                }
                // 等待一段时间重试
                delay(retryInterval)
            }
        }

        private fun write(array: List<Byte>) {
            engine(array) { pack ->
                when (pack) {
                    is TempPackage.Nothing -> logger?.log("nothing")
                    is TempPackage.Failed  -> logger?.log("failed")
                    is TempPackage.Data    -> {
                        val (temp, humi) = pack
                        logger?.log("temp = ${temp} [C], humi = ${humi} [%]")
                        launch {
                            thermometer.send(Stamped(System.currentTimeMillis(), Pair(temp, humi)))
                            dataWatchDog.feed()
                            launch { exceptions.send(ExceptionMessage.Recovered(dataTimeoutException)) }
                        }
                    }
                }
            }
        }
    }
