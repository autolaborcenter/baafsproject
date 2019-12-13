package com.marvelmind

import cn.autolabor.serialport.manager.SerialPortManager
import com.marvelmind.mobilebeacon.SerialPortMobileBeaconBuilderDsl.Companion.registerMobileBeacon
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.channel
import org.mechdancer.common.Stamped
import org.mechdancer.exceptions.ExceptionMessage
import kotlin.math.sqrt

// 静止状态下 marvelmind 标签定位相当准确
// 此测试用于计算静止状态下的定位标签位置方差
// 也可用于测试数据是否出现中断

@ObsoleteCoroutinesApi
fun main() {
    // 话题
    val beaconOnMap = channel<Stamped<Vector2D>>()
    val exceptions = channel<ExceptionMessage>()
    with(SerialPortManager(exceptions)) {
        registerMobileBeacon(beaconOnMap, exceptions)
        while (sync().isNotEmpty())
            Thread.sleep(100L)
    }
    runBlocking {
        val list = mutableListOf<Vector2D>()
        for ((_, p) in beaconOnMap) {
            list += vector2DOf(p.x, p.y)
            val sigmaX = list.asSequence().map { it.x - list.first().x }.sigma()
            val sigmaY = list.asSequence().map { it.y - list.first().y }.sigma()
            println("$sigmaX $sigmaY")
        }
    }
}

// 求标准差
fun Sequence<Double>.sigma(): Double {
    var ex = .0
    var ex2 = .0
    var i = 0
    for (it in this) {
        ++i
        ex += it
        ex2 += it * it
    }
    ex /= i
    ex2 /= i
    return sqrt(ex2 - ex * ex)
}
