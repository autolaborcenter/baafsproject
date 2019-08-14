import com.fazecast.jSerialComm.SerialPort
import serialport.parser.ParseEngine

object MarvelMindTest {
    @JvmStatic
    fun Array<String>.main() {
        SerialPort
            .getCommPorts()
            .filterNot { it.isOpen }
            .find {
                it.baudRate = 115200
                it.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 100, 100)
                if (!it.openPort())
                    return@find false
                val time = System.currentTimeMillis()
                val engine = ParseEngine(com.marvelmind.Parser())

                val array = ByteArray(32)
                var result = false
                while (!result && System.currentTimeMillis() - time < 1000) {
                    val actual = it.readBytes(array, array.size.toLong())
                    if (actual <= 0) continue
                    engine(array.asList().subList(0, actual)) { temp ->
                        result = (result || temp.code in 1..99).also { println(temp.code) }
                    }
                }

                result
            }
            ?.descriptivePortName
            .let(::println)
    }
}

object FaseLaseTest {
    @JvmStatic
    fun Array<String>.main() {
        val port = SerialPort.getCommPort("COM10")
        port.baudRate = 460800
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 100, 100)
        if (!port.openPort()) throw RuntimeException("open failed")
        val array = ByteArray(3)
        val engine = ParseEngine(com.faselase.Parser())
        while (true) {
            val actual = port.readBytes(array, array.size.toLong())
            if (actual <= 0) continue
            engine(array.asList().subList(0, actual)) { (rho, theta) ->
                if (rho >= 0) println("$rho , $theta")
            }
        }
    }
}
