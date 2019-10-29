package org.mechdancer.shape

import org.mechdancer.algebra.function.vector.minus
import org.mechdancer.algebra.implement.vector.Vector2D

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
            val (_, y) = point
            var p0 = vertex.last()
            vertex
                .asSequence()
                .count { p ->
                    val (_, y0) = p0
                    val (_, y1) = p
                    when (y) {
                        in y0..y1 -> {
                            val (xa, ya) = point - p0
                            val (xb, yb) = p - p0
                            xa * yb - xb * ya >= 0
                        }
                        in y1..y0 -> {
                            val (xa, ya) = point - p
                            val (xb, yb) = p0 - p
                            xa * yb - xb * ya >= 0
                        }
                        else      -> false
                    }.also { p0 = p }
                } % 2 == 1
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

