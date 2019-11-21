package cn.autolabor.pm1

import org.mechdancer.geometry.angle.Angle
import kotlin.math.PI
import kotlin.math.roundToInt

class IncrementalEncoder(pulsesPerRound: Int) {
    private val toRad = 2 * PI / pulsesPerRound
    operator fun get(pulses: Int) = pulses * toRad
    operator fun get(angle: Angle) = (angle.asRadian() / toRad).roundToInt()
}
