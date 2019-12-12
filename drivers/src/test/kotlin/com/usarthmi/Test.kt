package com.usarthmi

import cn.autolabor.serialport.manager.SerialPortManager
import com.usarthmi.UsartHmiBuilderDsl.Companion.registerUsartHmi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.mechdancer.channel
import org.mechdancer.exceptions.ExceptionMessage

@ObsoleteCoroutinesApi
fun main() {
    val exceptions = channel<ExceptionMessage>()
    val hmiMessages = channel<String>()

    val manager = SerialPortManager(exceptions)
    val hmi = manager.registerUsartHmi(hmiMessages)

    while (manager.sync().isNotEmpty())
        Thread.sleep(100L)

    runBlocking {
        hmi.write("page main")
        for (msg in hmiMessages) {
            println(msg)
            when (msg) {
                "load path\n'" -> {
                    hmi.write("b0.txt=\"记录\"")
                    hmi.write("state.val=0")
                    hmi.write("log.txt=\"正在运行\"")
                }
                "cancel"       -> {
                    hmi.write("b0.txt=\"记录\"")
                    hmi.write("state.val=0")
                    hmi.write("log.txt=\"\"")
                }
                "record"       -> {
                    hmi.write("b0.txt=\"保存\"")
                    hmi.write("state.val=1")
                    hmi.write("log.txt=\"正在记录\"")
                }
            }
        }
    }
}
