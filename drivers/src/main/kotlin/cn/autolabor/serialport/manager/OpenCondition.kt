package cn.autolabor.serialport.manager

import com.fazecast.jSerialComm.SerialPort

/** 打开条件 */
sealed class OpenCondition {
    object None : OpenCondition()
    class Certain(val name: String) : OpenCondition()
    class Filter(val predicate: (SerialPort) -> Boolean) : OpenCondition()
}
