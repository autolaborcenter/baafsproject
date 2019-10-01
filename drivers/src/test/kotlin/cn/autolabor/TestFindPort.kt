package cn.autolabor

import cn.autolabor.serialport.parser.SerialPortFinder.Companion.findSerialPort
import com.faselase.LidarPack.Data

object MarvelmindTest {
    @JvmStatic
    fun Array<String>.main() {
        findSerialPort(null, com.marvelmind.engine()) {
            baudRate = 115200
            timeoutMs = 1000
            bufferSize = 32
            condition { (code, _) -> code == 0x11 }
        }?.descriptivePortName.let(::println)
    }
}

object FaselaseTest {
    @JvmStatic
    fun Array<String>.main() {
        findSerialPort(null, com.faselase.engine()) {
            baudRate = 460800
            timeoutMs = 5000
            bufferSize = 32
            activate = "#SF 10\r\n".toByteArray(Charsets.US_ASCII)
            condition { pack -> pack is Data }
        }?.descriptivePortName.let(::println)
    }
}
