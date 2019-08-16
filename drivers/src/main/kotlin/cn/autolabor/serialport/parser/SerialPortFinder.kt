package cn.autolabor.serialport.parser

import com.fazecast.jSerialComm.SerialPort

/**
 * 通用串口查找器（DSL）
 */
class SerialPortFinder<T> private constructor() {
    /**
     * 波特率
     */
    var baudRate: Int = 9600

    /**
     * 临时缓存容量
     */
    var bufferSize: Int = 256

    /**
     * 查找超时时间
     */
    var timeoutMs: Int = 1000

    /**
     * 发送激活码
     */
    var activate: ByteArray = byteArrayOf()

    /**
     * 设置成功条件
     */
    fun condition(block: (T) -> Boolean) {
        predicate = block
    }

    // 判断谓词
    private var predicate: (T) -> Boolean = { false }

    companion object {
        /**
         * DSL
         *
         * @param engine 解析引擎
         * @param block  DSL
         */
        fun <T> findSerialPort(
            engine: ParseEngine<Byte, T>,
            block: SerialPortFinder<T>.() -> Unit) =
            SerialPortFinder<T>()
                .apply(block)
                .run {
                    SerialPort
                        .getCommPorts()
                        .find { port ->
                            // 设置串口
                            port.baudRate = baudRate
                            port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 100)
                            if (!port.openPort()) return@find false
                            // 发送激活码
                            if (activate.isNotEmpty()
                                && activate.size != port.writeBytes(activate, activate.size.toLong())
                            ) return@find false
                            // 初始化接收
                            val time = System.currentTimeMillis()
                            val array = ByteArray(bufferSize)
                            var result = false
                            // 接收并解析
                            while (!result && System.currentTimeMillis() - time < timeoutMs)
                                port.readBytes(array, array.size.toLong())
                                    .takeIf { it > 0 }
                                    ?.let { array.asList().subList(0, it) }
                                    ?.let { list -> engine(list) { result = result || predicate(it) } }
                            // 返回
                            if (!result) port.closePort()
                            result
                        }
                }
    }
}
