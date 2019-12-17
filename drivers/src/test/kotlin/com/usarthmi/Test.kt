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
        Thread.sleep(200L)

    runBlocking {
        var t0 = System.currentTimeMillis()
        var size = 0L
        hmi.write("page index")
        loop@ for (msg in hmiMessages) {
            println(msg)
            when (msg) {
                "record"       -> {
                    t0 = System.currentTimeMillis()
                    hmi.write("page record")
                }
                "load path\n'" -> {
                    hmi.write("page follow")
                    hmi.write("size.txt=\"全程${size}点\"")
                }
                "shut down"    -> {
                    hmi.write("page waiting")
                    break@loop
                }

                "save path"    -> {
                    size = (System.currentTimeMillis() - t0) / 200
                    hmi.write("t0.txt=\"${size}点已保存\"")
                }
                "cancel"       -> hmi.write("page index")
            }
        }
    }
}
