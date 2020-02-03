package cn.autolabor.pm1.model

import cn.autolabor.pm1.model.ControlVariable.Physical
import cn.autolabor.pm1.model.ControlVariable.Wheels
import org.mechdancer.algebra.function.vector.times
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.geometry.angle.Angle
import org.mechdancer.geometry.angle.toDegree
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.geometry.transformation.Pose2D
import org.mechdancer.geometry.transformation.pose2D
import kotlin.math.*

/** 机器人机械结构（计算模型） */
data class ChassisStructure(
    val width: Double,
    val leftRadius: Double,
    val rightRadius: Double,
    val length: Double
) {
    fun toDeltaOdometry(dl: Angle, dr: Angle): Pose2D {
        val l = dl.rad * leftRadius
        val r = dr.rad * rightRadius
        val length = (r + l) / 2
        return when (val theta = (r - l) / width) {
            .0   -> pose2D(length, 0)
            else -> Pose2D(p = Vector2D(sin(theta), (1 - cos(theta))) * (length / theta),
                           d = theta.toRad())
        }
    }

    fun toAngular(wheels: Wheels) =
        (wheels.l / leftRadius).toRad() to (wheels.r / rightRadius).toRad()

    fun toWheels(physical: Physical) =
        when {
            physical.speed == .0      -> {
                // 对于舵轮来说是奇点，无法恢复
                Wheels.static
            }
            physical.rudder.rad == .0 -> {
                // 直走
                Wheels(physical.speed, physical.speed)
            }
            else                      -> {
                // 圆弧
                val r = -length / tan(physical.rudder.rad)
                val k = (r + width / 2) / (r - width / 2)
                // 右转，左轮线速度快
                if (physical.rudder.rad > 0)
                    Wheels(physical.speed, physical.speed * k)
                // 左转，右轮线速度快
                else
                    Wheels(physical.speed / k, physical.speed)
            }
        }

    fun toPhysical(wheels: Wheels): Physical {
        val d = abs(wheels.l) - abs(wheels.r)
        return when {
            // d > 0 => |l| > |r| >= 0 => |l| > 0 => l != 0
            // 右转，左轮速度快
            d > 0               -> {
                val k = wheels.r / wheels.l
                val r = width / 2 * (k + 1) / (k - 1)
                Physical(wheels.l, (-atan(length / r)).toRad())
            }
            // d < 0 => |r| > |l| >= 0 => |r| > 0 => r != 0
            // 左转，右轮速度快
            d < 0               -> {
                val k = wheels.l / wheels.r
                val r = width / 2 * (1 + k) / (1 - k)
                Physical(wheels.r, (-atan(length / r)).toRad())
            }
            // 绝对值相等（两条对角线）
            wheels.l == .0      -> Physical(.0, Double.NaN.toDegree())
            wheels.l > wheels.r -> Physical(wheels.l, (+90).toDegree())
            wheels.r > wheels.l -> Physical(wheels.r, (-90).toDegree())
            else                -> Physical(wheels.l, 0.toDegree())
        }
    }

    fun toWheels(velocity: ControlVariable.Velocity) =
        (width / 2 * velocity.w.rad)
            .let { dv -> Wheels(velocity.v - dv, velocity.v + dv) }

    fun toVelocity(wheels: Wheels) =
        ControlVariable.Velocity(v = (wheels.r + wheels.l) / 2,
                                 w = ((wheels.r - wheels.l) / width).toRad())

    fun toPhysical(velocity: ControlVariable.Velocity): Physical =
        velocity.let(::toWheels).let(::toPhysical)

    fun toVelocity(physical: Physical): ControlVariable.Velocity =
        physical.let(::toWheels).let(::toVelocity)
}
