package cn.autolabor.serialport.manager

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel

internal interface SerialPortDevice {
    /** 标签 */
    val tag: String

    /** 开串口条件（名字条件） */
    val openCondition: OpenCondition

    /** 波特率 */
    val baudRate: Int

    /** 缓冲区容量 */
    val bufferSize: Int

    /** 重试间隔 */
    val retryInterval: Long

    /** 确认条件 */
    fun buildCertificator(): Certificator?

    val toDevice: ReceiveChannel<Iterable<Byte>>

    val toDriver: SendChannel<Iterable<Byte>>
}
