package cn.autolabor.serialport

import com.faselase.LidarPack
import com.fazecast.jSerialComm.SerialPort
import com.marvelmind.mobilebeacon.BeaconPackage
import com.marvelmind.mobilebeacon.engine

object MarvelmindTest {
    @JvmStatic
    fun Array<String>.main() {
        SerialPortFinder.findSerialPort(
            candidates = SerialPort.getCommPorts().toList(),
            engine = engine()
        ) {
            baudRate = 115200
            timeoutMs = 1000
            bufferSize = 32
            condition { it is BeaconPackage.Coordinate }
        }.descriptivePortName.let(::println)
    }
}

object FaselaseTest {
    @JvmStatic
    fun Array<String>.main() {
        SerialPortFinder.findSerialPort(
            candidates = SerialPort.getCommPorts().toList(),
            engine = com.faselase.engine()
        ) {
            baudRate = 460800
            timeoutMs = 5000
            bufferSize = 32
            activate = "#SF 10\r\n".toByteArray(Charsets.US_ASCII)
            condition { it is LidarPack.Data }
        }.descriptivePortName.let(::println)
    }
}
