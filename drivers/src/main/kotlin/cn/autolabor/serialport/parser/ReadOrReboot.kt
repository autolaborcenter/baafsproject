package cn.autolabor.serialport.parser

import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.delay

/**
 *
 */
internal suspend fun SerialPort.readOrReboot(
    buffer: ByteArray,
    retryInterval: Long,
    block: suspend () -> Unit
): List<Byte> {
    val size = buffer.size.toLong()
    // 反复尝试读取
    while (true) {
        // 在单线程上打开串口并阻塞读取
        when (val actual = takeIf { it.isOpen || it.openPort() }?.readBytes(buffer, size)) {
            null, -1 ->
                block()
            0        -> {
                // 如果长度是 0,的可能是假的,发送空包可更新串口对象状态
                writeBytes(byteArrayOf(), 0)
                if (!isOpen) block()
            }
            else     ->
                return buffer.take(actual)
        }
        // 等待一段时间重试
        delay(retryInterval)
    }
}
