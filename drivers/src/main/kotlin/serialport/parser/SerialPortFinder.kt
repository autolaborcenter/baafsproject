package serialport.parser

import com.fazecast.jSerialComm.SerialPort

class SerialPortFinder<T> private constructor() {
    var baudRate: Int = 9600
    var bufferSize: Int = 256
    var timeoutMs: Int = 1000

    var activate: ByteArray = byteArrayOf()
    private var predicate: (T) -> Boolean = { false }

    fun condition(block: (T) -> Boolean) {
        predicate = block
    }

    companion object {
        fun <T> findSerialPort(
            engine: ParseEngine<Byte, T>,
            block: SerialPortFinder<T>.() -> Unit) =
            SerialPortFinder<T>()
                .apply(block)
                .run {
                    SerialPort
                        .getCommPorts()
                        .find { port ->
                            port.baudRate = baudRate
                            port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 100, 100)
                            if (!port.openPort()) return@find false

                            if (activate.isNotEmpty()
                                && activate.size != port.writeBytes(activate, activate.size.toLong())
                            ) return@find false

                            val time = System.currentTimeMillis()

                            val array = ByteArray(bufferSize)
                            var result = false

                            while (!result && System.currentTimeMillis() - time < timeoutMs) {
                                val actual = port.readBytes(array, array.size.toLong()).also(::println)
                                if (actual <= 0) continue
                                engine(array.asList().subList(0, actual)) { temp ->
                                    result = result || predicate(temp)
                                }
                            }

                            result
                        }
                }
    }
}
