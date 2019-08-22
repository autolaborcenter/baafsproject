package cn.autolabor.transform

import org.mechdancer.algebra.core.Matrix
import org.mechdancer.algebra.core.Vector
import org.mechdancer.algebra.function.matrix.dim
import org.mechdancer.algebra.function.matrix.isNotSquare
import org.mechdancer.algebra.function.matrix.times
import org.mechdancer.algebra.implement.matrix.builder.matrix
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.toListVector
import org.mechdancer.geometry.angle.Angle
import org.mechdancer.geometry.angle.toVector

/**
 * 变换
 */
class Transformation(private val matrix: Matrix) {
    val dim get() = matrix.dim - 1

    init {
        if (matrix.isNotSquare())
            throw IllegalArgumentException("transform matrix must be square")
    }

    operator fun invoke(vector: Vector): Vector {
        if (matrix.dim - vector.dim != 1)
            throw IllegalArgumentException("a ${dim}D Transformation cannot transform ${vector.dim}D vector")
        return matrix * (vector.toList() + 1.0).toListVector()
    }

    operator fun times(others: Transformation) =
        Transformation(matrix * others.matrix)

    companion object {
        /**
         * 将位姿转化为位姿上坐标系的变换
         */
        fun tansformation(
            p: Vector2D,
            d: Angle
        ) = d.toVector()
            .let {
                matrix {
                    row(+it.x, +it.y, +p.x)
                    row(-it.y, +it.x, +p.y)
                    row(0, 0, 1)
                }
            }
            .let(::Transformation)
    }
}
