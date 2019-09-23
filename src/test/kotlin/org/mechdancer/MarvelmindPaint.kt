package org.mechdancer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.modules.await
import org.mechdancer.modules.devices.Locator.FrameworkRemoteLocator.MarvelmindLocator

fun main() {
    val scope = CoroutineScope(Dispatchers.Default)
    val list = mutableListOf<Vector2D>()
    scope.launch {
        for ((_, v) in MarvelmindLocator(this).robotLocation) {
            list += vector2DOf(v.x, v.y)
            val sigmaX = list.asSequence().map { it.x - list.first().x }.sigma()
            val sigmaY = list.asSequence().map { it.y - list.first().y }.sigma()
            println("$sigmaX $sigmaY")
        }
    }
    scope.await()
}

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
    return ex2 - ex * ex
}
