package cn.autolabor.pathfollower.shape

import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.algebra.function.vector.times
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.geometry.angle.toVector
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 半径为 [radius] 的正圆，并从等面积正多边形上等间隔采样 [vertex] 个点作为顶点
 */
class Circle(val radius: Double, vertexCount: Int = 32)
    : Shape(
    (2 * PI / vertexCount)
        .let { theta -> radius / sqrt(sin(theta) / theta) }
        .let { equivalent -> List(vertexCount) { i -> (i * 2 * PI / vertexCount).toRad().toVector() * equivalent } }
) {
    init {
        require(radius >= 0) { "radius must not less than .0" }
    }

    override operator fun contains(point: Vector2D) = point.norm() < radius

    override fun calculateSize() = PI * radius * radius
}
