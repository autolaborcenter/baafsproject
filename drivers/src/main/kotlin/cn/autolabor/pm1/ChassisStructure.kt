package cn.autolabor.pm1

import org.mechdancer.algebra.function.vector.times
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.common.Odometry
import org.mechdancer.geometry.angle.Angle
import org.mechdancer.geometry.angle.toRad
import kotlin.math.cos
import kotlin.math.sin

data class ChassisStructure(
    val width: Double,
    val leftRadius: Double,
    val rightRadius: Double,
    val length: Double
) {
    fun transform(dl: Angle, dr: Angle): Odometry {
        val l = dl.asRadian()
        val r = dr.asRadian()
        val length = (r + l) / 2
        return when (val theta = (r - l) / width) {
            .0   -> Odometry.pose(length, 0)
            else -> Odometry(vector2DOf(sin(theta), (1 - cos(theta))) * (length / theta),
                             theta.toRad())
        }
    }
}
