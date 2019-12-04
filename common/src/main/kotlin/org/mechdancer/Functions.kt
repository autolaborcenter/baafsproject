package org.mechdancer

import kotlinx.coroutines.channels.Channel
import org.mechdancer.algebra.doubleEquals
import org.mechdancer.algebra.function.vector.div
import org.mechdancer.algebra.function.vector.plus
import org.mechdancer.algebra.function.vector.times
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOfZero
import org.mechdancer.common.Odometry
import org.mechdancer.geometry.angle.toAngle
import org.mechdancer.geometry.angle.toVector
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sqrt

/** 构造发送无阻塞的通道 */
fun <T> channel() = Channel<T>(Channel.CONFLATED)

private fun averageVectors(list: Iterable<Pair<Vector2D, Double>>): Vector2D =
    list.fold(vector2DOfZero()) { sum, (v, w) -> sum + v * w } /
    list.sumByDouble { (_, w) -> w }

/** 里程的加权平均 */
fun average(a: Pair<Odometry, Double>,
            b: Pair<Odometry, Double>): Odometry {
    val (x0, r0) = a
    val (x1, r1) = b
    val (p0, d0) = x0
    val (p1, d1) = x1
    return Odometry(
            p = averageVectors(listOf(p0 to r0, p1 to r1)),
            d = averageVectors(listOf(d0.toVector() to r0, d1.toVector() to r1)).toAngle())
}

/** 数值积分 */
fun integral(
    x0: Double,
    x1: Double,
    stepLength: Double? = null,
    function: (Double) -> Double
): Double = when {
    doubleEquals(x0, x1) -> .0
    x0 > x1              -> -integral(x1, x0, stepLength, function)
    else                 -> {
        val step = stepLength ?: (x1 - x0) * 1E-4
        var xn = x0 + step
        var yn = function(x0)
        var sum = .0
        while (xn < x1) {
            xn += step
            val `yn-1` = yn
            yn = function(xn)
            sum += (`yn-1` + yn) * step / 2
        }
        sum
    }
}

fun erf(x: Double, stepLength: Double? = null) =
    2.0 / sqrt(PI) * integral(.0, x, stepLength) { eta -> exp(-eta * eta) }

fun erfc(x: Double, stepLength: Double? = null) =
    1 - erf(x, stepLength)

fun CDFNormal(eta: Double, sigma: Double, x: Double, stepLength: Double? = null) =
    erfc((eta - x) / (sqrt(2.0) * sigma), stepLength) / 2
