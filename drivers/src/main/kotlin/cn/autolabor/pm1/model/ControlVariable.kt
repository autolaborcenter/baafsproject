package cn.autolabor.pm1.model

import org.mechdancer.geometry.angle.Angle
import org.mechdancer.geometry.angle.toRad

/** 控制量 */
sealed class ControlVariable {
    data class Velocity(val v: Double, val w: Angle) : ControlVariable() {
        companion object {
            val static = Velocity(.0, 0.toRad())
        }
    }

    data class Wheels(val l: Double, val r: Double) : ControlVariable() {
        companion object {
            val static = Wheels(.0, .0)
        }
    }

    data class Physical(val speed: Double, val rudder: Angle) : ControlVariable() {
        companion object {
            val static = Physical(.0, Double.NaN.toRad())
        }
    }
}
