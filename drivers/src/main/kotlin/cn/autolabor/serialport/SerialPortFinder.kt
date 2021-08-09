package cn.autolabor.serialport

import cn.autolabor.serialport.parser.ParseEngine
import com.fazecast.jSerialComm.SerialPort
import org.mechdancer.annotations.BuilderDslMarker

/**
 * 通用串口查找器（DSL）
 */
@BuilderDslMarker
class SerialPortFinder<T> private constructor() {
    /** 波特率 */
    var baudRate: Int = 9600

    /** 临时缓存容量 */
    var bufferSize: Int = 256

    /** 查找超时时间 */
    var timeoutMs: Long = 1000L

    /** 发送激活码 */
    var activate: ByteArray = byteArrayOf()

    /** 设置成功条件 */
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
            candidates: List<SerialPort>,
            engine: ParseEngine<Byte, T>,
            block: SerialPortFinder<T>.() -> Unit
        ) =
            if (candidates.isEmpty())
                throw RuntimeException("no available port")
            else
                SerialPortFinder<T>()
                    .apply(block)
                    .run {
                        val exceptionMessages = mutableListOf<String>()
                        candidates
                            .find { port ->
                                println("try ${port.systemPortName} -> ${port.descriptivePortName}")
                                // 设置串口
                                port.baudRate = baudRate
                                port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 100)
                                if (!port.openPort()) {
                                    exceptionMessages += "${port.systemPortName}: cannot open"
                                    return@find false
                                }
                                // 发送激活码
                                if (activate.isNotEmpty()
                                    && activate.size != port.writeBytes(activate, activate.size.toLong())
                                ) {
                                    exceptionMessages += "${port.systemPortName}: failed to send activation"
                                    return@find false
                                }
                                // 初始化接收
                                val time = System.currentTimeMillis()
                                val array = ByteArray(bufferSize)
                                var result = false
                                // 接收并解析
                                while (!result && System.currentTimeMillis() - time < timeoutMs)
                                    port.readBytes(array, array.size.toLong())
                                        .takeIf { it > 0 }
                                        ?.let(array::take)
                                        ?.let { list -> engine(list) { result = result || predicate(it) } }
                                // 返回
                                if (!result) {
                                    exceptionMessages += "${port.systemPortName}: never receive any valid data until timeout"
                                    port.closePort()
                                }
                                result
                            }
                            ?: throw RuntimeException(exceptionMessages.joinToString("\n"))
                    }
    }
}
