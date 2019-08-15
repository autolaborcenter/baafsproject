package cn.autolabor

import cn.autolabor.serialport.parser.SerialPortFinder.Companion.findSerialPort

object MarvelmindTest {
    @JvmStatic
    fun Array<String>.main() {
        findSerialPort(com.marvelmind.engine()) {
            baudRate = 115200
            timeoutMs = 1000
            bufferSize = 32
            condition { (code, _) -> code == 0x11 }
        }
            ?.descriptivePortName
            .let(::println)
    }
}

object FaselaseTest {
    @JvmStatic
    fun Array<String>.main() {
        findSerialPort(com.faselase.engine(filter = true)) {
            baudRate = 460800
            timeoutMs = 5000
            bufferSize = 32
            activate = "#SF 10\r\n".toByteArray(Charsets.US_ASCII)
            condition { (rho, _) -> rho > 0 }
        }
            ?.descriptivePortName
            .let(::println)
    }
}
