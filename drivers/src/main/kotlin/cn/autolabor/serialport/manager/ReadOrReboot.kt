package cn.autolabor.serialport.manager

import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.delay

/**
 * 从串口读取，并在超时时自动重启串口
 * @param buffer 缓冲区
 * @param retryInterval 重试间隔
 * @param block 异常报告回调
 */
internal suspend fun SerialPort.readOrReboot(
    buffer: ByteArray,
    retryInterval: Long,
    block: suspend () -> Unit = {}
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

// 写重连阈值(超过此次数写失败则串口重连)
internal const val WRITE_RECON_CNT = 5
// 写计数
internal var writeFailCnt = 0

/**
 * 从串口读取，并在超时时自动重启串口
 * @param buffer 缓冲区
 * @param retryInterval 重试间隔
 * @param block 异常报告回调
 */
internal suspend fun SerialPort.writeOrReboot(
    buffer: ByteArray,
    retryInterval: Long,
    block: suspend () -> Unit = {}
): Boolean {
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
                    block()
            }
        }
        // 等待一段时间重试
        delay(retryInterval)
    }
}

