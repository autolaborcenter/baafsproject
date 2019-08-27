package cn.autolabor.pathfollower

import org.mechdancer.algebra.function.vector.minus
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
    init {
        require(vertex.size >= 3) { "vertex count must be more than 3" }
    }

    val size by lazy { calculateSize() }

    open operator fun contains(point: Vector2D) =
        (0 until vertex.lastIndex)
            .sumByDouble { i ->
                val a = vertex[i] - point
                val b = vertex[i + 1] - point
                abs(a.x * b.y - a.y * b.x)
            }
            .let { abs(size - it / 2) < 1E-6 }

    protected open fun calculateSize() =
        (0 until vertex.lastIndex)
            .sumByDouble { i ->
                val a = vertex[i]
                val b = vertex[i + 1]
                a.x * b.y - a.y * b.x
            } / 2
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
