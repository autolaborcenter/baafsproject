package org.mechdancer.baafs

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.baafs.modules.LinkMode.Direct
import org.mechdancer.baafs.modules.startBeacon
import org.mechdancer.channel
import org.mechdancer.common.Stamped
import kotlin.math.sqrt

fun main() = runBlocking<Unit> {
    // 话题
    val beaconOnMap = channel<Stamped<Vector2D>>()
    // 任务
    startBeacon(
        mode = Direct,
        beaconOnMap = beaconOnMap)
    val list = mutableListOf<Vector2D>()
    launch {
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
