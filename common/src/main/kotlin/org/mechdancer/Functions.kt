package org.mechdancer

import kotlinx.coroutines.channels.Channel
import org.mechdancer.algebra.doubleEquals
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sqrt

/** 构造发送无阻塞的通道 */
fun <T> channel() = Channel<T>(Channel.CONFLATED)

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
