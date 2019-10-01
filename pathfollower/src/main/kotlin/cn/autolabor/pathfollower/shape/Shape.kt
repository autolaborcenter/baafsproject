package cn.autolabor.pathfollower.shape

import org.mechdancer.algebra.implement.vector.Vector2D
import kotlin.math.abs

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

