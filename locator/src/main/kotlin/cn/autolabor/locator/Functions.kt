package cn.autolabor.locator

import org.mechdancer.algebra.function.vector.div
import org.mechdancer.algebra.function.vector.plus
import org.mechdancer.algebra.function.vector.times
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOfZero
import org.mechdancer.common.Odometry
import org.mechdancer.geometry.angle.toAngle
import org.mechdancer.geometry.angle.toVector

private fun averageVectors(list: Iterable<Pair<Vector2D, Double>>): Vector2D =
    list.fold(vector2DOfZero()) { sum, (v, w) -> sum + v * w } /
    list.sumByDouble { (_, w) -> w }

/** 里程计的加权平均 */
internal fun average(a: Pair<Odometry, Double>,
                     b: Pair<Odometry, Double>
): Odometry {
    val (x0, r0) = a
    val (x1, r1) = b
    val (p0, d0) = x0
    val (p1, d1) = x1
    return Odometry(
            p = averageVectors(listOf(p0 to r0, p1 to r1)),
            d = averageVectors(listOf(d0.toVector() to r0, d1.toVector() to r1)).toAngle())
}
