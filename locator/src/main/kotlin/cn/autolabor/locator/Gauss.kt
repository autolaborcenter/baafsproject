package cn.autolabor.locator

import java.util.*
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

class Gauss(val mu: Double, val sigma: Double) {
    private val k0 = +1 / (sigma * sqrt(2 * PI))
    private val k1 = -1 / (2 * sigma * sigma)

    fun next() = random.nextGaussian() * sigma + mu
    fun p(value: Double) = k0 * exp(k1 * (value - sigma).pow(2))

    private companion object {
        val random = Random()
    }
}
