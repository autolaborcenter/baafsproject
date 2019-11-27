package cn.autolabor.pm1.model

import org.mechdancer.geometry.angle.Angle
import org.mechdancer.geometry.angle.toRad
import kotlin.math.PI
import kotlin.math.roundToInt

/** 增量式编码器（计算模型） */
internal class IncrementalEncoder(pulsesPerRound: Int) {
    private val toRad = 2 * PI / pulsesPerRound
    fun toAngular(pulses: Number) = (pulses.toDouble() * toRad).toRad()
    fun toPulses(angle: Angle) = (angle.asRadian() / toRad).roundToInt()
}
