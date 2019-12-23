package cn.autolabor.locator

import org.mechdancer.algebra.function.vector.times
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOfZero
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.geometry.angle.toVector
import kotlin.math.PI
import kotlin.random.Random

/** 偶发定位异常 */
internal class AccidentalBeaconErrorSource(
    private val pStart: Double,
    private val pStop: Double,
    private val range: Double
) {
    private var error: Vector2D? = null
    fun next() =
        with(Random) {
            when {
                error == null && nextDouble() < pStart ->
                    nextDouble(-PI, +PI).toRad().toVector() * nextDouble(.0, range)
                error != null && nextDouble() < pStop  ->
                    null
                else                                   ->
                    error
            }
        }.also { error = it }
        ?: vector2DOfZero()
}
