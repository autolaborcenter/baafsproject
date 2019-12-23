package com.usarthmi

import cn.autolabor.serialport.manager.SerialPortManager
import com.usarthmi.UsartHmiBuilderDsl.Companion.registerUsartHmi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.delay
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
        Thread.sleep(200L)

    runBlocking {
        var t0 = System.currentTimeMillis()
        var size: Long
        hmi.page = UsartHmi.Page.Index
        loop@ for (msg in hmiMessages) {
            println(msg)
            when (msg) {
                "record"       -> {
                    t0 = System.currentTimeMillis()
                    hmi.page = UsartHmi.Page.Record
                }
                "load path\n'" -> {
                    hmi.page = UsartHmi.Page.Follow
                }
                "shut down"    -> {
                    hmi.page = UsartHmi.Page.Waiting
                    delay(100L)
                    break@loop
                }

                "save path"    -> {
                    size = (System.currentTimeMillis() - t0) / 200
                    hmi.write("t0.txt=\"${size}点已保存\"")
                }
                "cancel"       ->
                    hmi.page = UsartHmi.Page.Index
            }
        }
    }
}
