import serialport.parser.SerialPortFinder.Companion.findSerialPort

object MarvelMindTest {
    @JvmStatic
    fun Array<String>.main() {
        findSerialPort(com.marvelmind.engine()) {
            baudRate = 115200
            timeoutMs = 1000
            condition { (code, _) -> code == 0x11 }
        }
            ?.descriptivePortName
            .let(::println)
    }
}

object FaseLaseTest {
    @JvmStatic
    fun Array<String>.main() {
        findSerialPort(com.faselase.engine(true)) {
            baudRate = 460800
            timeoutMs = 5000
            activate = "#SF 10\r\n".toByteArray(Charsets.US_ASCII)
            condition { (rho, _) -> rho > 0 }
        }
            ?.descriptivePortName
            .let(::println)
    }
}
