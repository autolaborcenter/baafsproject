package cn.autolabor.pm1

import org.mechdancer.geometry.angle.Angle
import org.mechdancer.geometry.angle.toRad
import kotlin.math.PI
import kotlin.math.roundToInt

/** 增量式编码器（计算模型） */
class IncrementalEncoder(pulsesPerRound: Int) {
    private val toRad = 2 * PI / pulsesPerRound
    operator fun get(pulses: Number) = (pulses.toDouble() * toRad).toRad()
    operator fun get(angle: Angle) = (angle.asRadian() / toRad).roundToInt()
}
