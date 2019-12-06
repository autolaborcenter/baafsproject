package com.usarthmi

import cn.autolabor.serialport.manager.SerialPortManager
import kotlinx.coroutines.runBlocking
import org.mechdancer.channel
import org.mechdancer.exceptions.ExceptionMessage

fun main() {
    val exceptions = channel<ExceptionMessage>()
    val hmiMessages = channel<String>()

    val manager = SerialPortManager(exceptions)
    val hmi = UsartHmi(portName = "COM3", msgFromHmi = hmiMessages)
    manager.register(hmi)

    while (manager.sync().isNotEmpty())
        Thread.sleep(100L)

    runBlocking {
        for (msg in hmiMessages) {
            println(msg)
            hmi.write(msg)
        }
    }
}
