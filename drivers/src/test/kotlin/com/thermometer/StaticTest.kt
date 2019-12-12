package com.thermometer

import cn.autolabor.serialport.manager.SerialPortManager
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.channel
import org.mechdancer.common.Stamped
import org.mechdancer.exceptions.ExceptionMessage

// 测试温湿度计，每秒读取一次
@ObsoleteCoroutinesApi
fun main() {
    // 话题
    val beaconOnMap = channel<Stamped<Vector2D>>()
    val exceptions = channel<ExceptionMessage>()
    with(SerialPortManager(exceptions)) {
        register(SerialPortTemperX(null))
        while (true) {
            println(sync().takeUnless(Collection<*>::isEmpty) ?: break)
            Thread.sleep(100L)
        }
    }
    Thread.sleep(10_000)
}
