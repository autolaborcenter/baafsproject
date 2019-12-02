package cn.autolabor.serialport.parser.new

import com.fazecast.jSerialComm.SerialPort

internal sealed class OpenCondition {
    object None : OpenCondition()
    class Certain(val name: String) : OpenCondition()
    class Filter(val predicate: (SerialPort) -> Boolean) : OpenCondition()
}
