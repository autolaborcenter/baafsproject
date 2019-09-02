package com.faselase

import kotlin.math.cos
import kotlin.math.sin

/** 极坐标 */
data class Polar(val distance: Double, val angle: Double) {
    val x get() = distance * cos(angle)
    val y get() = distance * sin(angle)
}
