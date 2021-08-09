package cn.autolabor.serialport.manager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel

interface SerialPortDevice {
    /** 标签 */
    val tag: String

    /** 开串口条件（名字条件） */
    val openCondition: OpenCondition

    /** 波特率 */
    val baudRate: Int

    /** 缓冲区容量 */
    val bufferSize: Int

    /** 确认条件 */
    fun buildCertificator(): Certificator?

    /** 启动 */
    fun setup(
        scope: CoroutineScope,
        toDevice: SendChannel<List<Byte>>,
        fromDevice: ReceiveChannel<List<Byte>>
    )
}
