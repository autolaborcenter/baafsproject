package cn.autolabor

import com.marvelmind.MobileBeaconException
import com.marvelmind.MobileBeaconModuleBuilderDsl.Companion.startMobileBeacon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

fun main() = runBlocking<Unit>(Dispatchers.Default) {
    // 话题
    val beaconOnMap = channel<Stamped<Vector2D>>()
    val exceptions = channel<ExceptionMessage<MobileBeaconException>>()
    // 任务
    startMobileBeacon(
        beaconOnMap = beaconOnMap,
        exceptions = exceptions)
    val list = mutableListOf<Vector2D>()
    launch {
        for ((_, p) in beaconOnMap) {
            list += vector2DOf(p.x, p.y)
            val sigmaX = list.asSequence().map { it.x - list.first().x }.sigma()
            val sigmaY = list.asSequence().map { it.y - list.first().y }.sigma()
            println("$sigmaX $sigmaY")
        }
    }
    launch {
        for (e in exceptions)
            if (e is ExceptionMessage.Occurred<*>)
                println(e.what)
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
