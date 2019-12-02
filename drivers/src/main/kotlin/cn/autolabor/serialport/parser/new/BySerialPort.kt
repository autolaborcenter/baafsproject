package cn.autolabor.serialport.parser.new

internal interface BySerialPort {
    /** 开串口条件（名字条件） */
    val openCondition: OpenCondition

    /** 波特率 */
    val baudRate: Int

    /** 缓冲区容量 */
    val bufferSize: Int

    /** 确认条件 */
    fun buildCertificator(): Certificator?

    /** 使用串口数据 */
    fun read(bytes: Iterable<Byte>)
}
