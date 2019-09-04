package cn.autolabor.pathfollower

import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.algebra.function.vector.times
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 任意以 [vertex] 为顶点的**凸多边形**
 *
 * 若涉及移动问题，应将图形的重心设为 `{0, 0}`
 */
open class Shape(val vertex: List<Vector2D>) {
    val size by lazy { calculateSize() }

    open operator fun contains(point: Vector2D): Boolean =
        if (vertex.size < 3) false
        else {
            var last = vertex[0]
            vertex.drop(1)
                .sumByDouble {
                    val other = last
                    last = it
                    abs(other.x * it.y - other.y * it.x)
                }
                .let { abs(it / 2 - size) < 1E-6 }
        }

    protected open fun calculateSize(): Double =
        if (vertex.size < 3) .0
        else {
            var last = vertex[0]
            vertex.drop(1)
                .sumByDouble {
                    val other = last
                    last = it
                    other.x * it.y - other.y * it.x
                } / 2
        }
}

/**
 * 半径为 [radius] 的正圆，并从其上等间隔采样 [vertex] 个点作为顶点
 */
class Circle(val radius: Double, vertexCount: Int = 32)
    : Shape(run {
    val step = 2 * PI / vertexCount
    List(vertexCount) { i ->
        val theta = i * step
        vector2DOf(cos(theta), sin(theta)) * radius
    }
}) {
    init {
        require(radius >= 0) { "radius must not less than .0" }
    }

    override operator fun contains(point: Vector2D) = point.norm() < radius

    override fun calculateSize() = PI * radius * radius
}
