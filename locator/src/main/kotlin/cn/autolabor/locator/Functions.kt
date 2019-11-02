package cn.autolabor.locator

import org.mechdancer.algebra.function.vector.div
import org.mechdancer.algebra.function.vector.plus
import org.mechdancer.algebra.function.vector.times
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.common.Odometry
import org.mechdancer.geometry.angle.toAngle
import org.mechdancer.geometry.angle.toVector

private fun averageVectors(a: Pair<Vector2D, Double>,
                           b: Pair<Vector2D, Double>
): Vector2D {
    val (v0, r0) = a
    val (v1, r1) = b
    return (v0 * r0 + v1 * r1) / (r0 + r1)
}

fun average(a: Pair<Odometry, Double>,
            b: Pair<Odometry, Double>
): Odometry {
    val (x0, r0) = a
    val (x1, r1) = b
    val (p0, d0) = x0
    val (p1, d1) = x1
    return Odometry(
        p = averageVectors(p0 to r0, p1 to r1),
        d = averageVectors(d0.toVector() to r0, d1.toVector() to r1).toAngle())
}
