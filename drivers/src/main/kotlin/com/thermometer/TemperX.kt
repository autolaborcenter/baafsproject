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
import org.mechdancer.exceptions.ExceptionMessage.Occurred
import org.mechdancer.exceptions.device.DataTimeoutException
import org.mechdancer.exceptions.device.DeviceNotExistException
import org.mechdancer.exceptions.device.DeviceOfflineException
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * 温湿度计驱动
 */
class TemperX(
    scope: CoroutineScope,
    private val thermometer: SendChannel<Stamped<Pair<Double, Double>>>,
    private val exceptions: SendChannel<ExceptionMessage>,
    portName: String?,      // 串口号
    dataTimeout: Long,      // 数据超时时间
    private val retryInterval: Long,    // 串口重试间隔
    mainInterval: Long,     // 读取温度周期
    private val logger: SimpleLogger?
) : CoroutineScope by scope {
    // 超时异常监控
    private val dataTimeoutException = DataTimeoutException(NAME, dataTimeout)
    private val dataWatchDog =
        WatchDog(this, dataTimeout)
        { exceptions.send(Occurred(dataTimeoutException)) }

    // 常量参数
    private companion object {
        const val NAME = "temperx232"
        const val BUFFER_SIZE = 64

        val CMD = "ReadTemp".toByteArray(Charsets.US_ASCII)
        private const val PacketHead = "Temp-Inner:"
        private const val PacketLenMin = 33
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
                if (port.writeOrReboot(CMD)) {
                    port.readBytes(buffer, BUFFER_SIZE.toLong())
                        .takeIf { it > 0 }
                        ?.let { buffer.copyOfRange(0, it) }
                        ?.toString(Charsets.US_ASCII)
                        ?.takeIf { it.startsWith(PacketHead) && it.length >= PacketLenMin }
                        ?.runCatching {
                            val idx1 = indexOf(':') + 1
                            val idx2 = indexOf("[C]") - 1
                            val idx3 = indexOf(',') + 1
                            val idx4 = indexOf("[%") - 1
                            val temp = substring(idx1, idx2).toDouble()
                            val humi = substring(idx3, idx4).toDouble()
                            logger?.log("temp = ${temp}[C], humi = $humi%")
                            launch {
                                thermometer.send(Stamped(System.currentTimeMillis(), Pair(temp, humi)))
                                dataWatchDog.feed()
                                launch { exceptions.send(ExceptionMessage.Recovered(dataTimeoutException)) }
                            }
                        }
                }
                delay(max(1, start + mainInterval - System.currentTimeMillis()))
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
            if (takeIf { it.isOpen || it.openPort() }?.writeBytes(buffer, buffer.size.toLong()) == buffer.size) {
                writeFailCnt = 0
                return true
            } else {
                writeFailCnt++
                if (writeFailCnt >= WRITE_RECON_CNT)
                    exceptions.send(Occurred(DeviceOfflineException(NAME)))
            }
            // 等待一段时间重试
            delay(retryInterval)
        }
    }
}
