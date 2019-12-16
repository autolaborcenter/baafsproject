package com.thermometer

import cn.autolabor.serialport.manager.SerialPortManager
import com.thermometer.SerialPortTemperXBuilderDsl.Companion.registerTemperX
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.runBlocking
import org.mechdancer.channel
import org.mechdancer.common.Stamped
import org.mechdancer.exceptions.ExceptionMessage

// 测试温湿度计，每秒读取一次
@ObsoleteCoroutinesApi
fun main() {
    // 话题
    val temperatures = channel<Stamped<Humiture>>()
    val exceptions = channel<ExceptionMessage>()
    with(SerialPortManager(exceptions)) {
        registerTemperX(temperatures, exceptions)
        while (true) {
            println(sync().takeUnless(Collection<*>::isEmpty) ?: break)
            Thread.sleep(100L)
        }
    }
    runBlocking { temperatures.consumeEach(::println) }
}
