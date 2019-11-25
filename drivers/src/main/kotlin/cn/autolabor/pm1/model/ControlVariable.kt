package cn.autolabor.pm1.model

import org.mechdancer.geometry.angle.Angle

/** 控制量 */
sealed class ControlVariable {
    data class Velocity(val v: Double, val w: Angle) : ControlVariable()
    data class Wheels(val l: Double, val r: Double) : ControlVariable()
    data class Physical(val speed: Double, val rudder: Angle) : ControlVariable()
}
