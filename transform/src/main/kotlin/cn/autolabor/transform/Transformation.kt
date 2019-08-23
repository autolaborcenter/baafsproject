package cn.autolabor.transform

import org.mechdancer.algebra.core.Matrix
import org.mechdancer.algebra.core.Vector
import org.mechdancer.algebra.core.matrixView
import org.mechdancer.algebra.function.matrix.dim
import org.mechdancer.algebra.function.matrix.inverse
import org.mechdancer.algebra.function.matrix.times
import org.mechdancer.algebra.function.vector.select
import org.mechdancer.algebra.implement.matrix.builder.matrix
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.toListVector
import org.mechdancer.geometry.angle.Angle
import org.mechdancer.geometry.angle.toVector

/** 用齐次变换矩阵 [matrix] 存储的变换关系 */
class Transformation(private val matrix: Matrix) {
    /** 变换的维数 */
    val dim = matrix
                  .dim
                  .takeIf { it > 0 }
                  ?.let { it - 1 }
              ?: throw IllegalArgumentException("transform matrix must be square")

    /** 对 [vector] 应用变换 */
    operator fun invoke(vector: Vector): Vector {
        if (matrix.dim - vector.dim != 1)
            throw IllegalArgumentException("a ${dim}D Transformation cannot transform ${vector.dim}D vector")
        return (matrix * (vector.toList() + 1.0).toListVector()).select(0 until dim)
    }

    /** 串联另一个变换 [others] */
    operator fun times(others: Transformation) =
        Transformation(matrix * others.matrix)

    /** 求逆变换 */
    operator fun unaryMinus() =
        Transformation(matrix.inverse())

    override fun toString() = matrix.matrixView()

    companion object {
        /**
         * 将位姿转化为位姿上坐标系的变换
         */
        fun fromPose(
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
