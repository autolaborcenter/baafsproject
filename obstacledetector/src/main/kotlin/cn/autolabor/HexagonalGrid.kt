package cn.autolabor

import org.mechdancer.algebra.function.matrix.times
import org.mechdancer.algebra.function.matrix.unaryMinus
import org.mechdancer.algebra.function.vector.div
import org.mechdancer.algebra.function.vector.times
import org.mechdancer.algebra.implement.matrix.builder.matrix
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * 到这个坐标系的变换矩阵
 * |60°/
 * |  /
 * | /
 * |/
 * .
 */
private val hexTransform =
    matrix {
        row(cos(-PI / 6), 0)
        row(sin(-PI / 6), 1)
    }

private val pixelTransform =
    -hexTransform

/** 转换到平面六角坐标系格点 */
fun Polar.toHexagonal(unit: Double): Pair<Int, Int> =
    (hexTransform * vector2DOf(x, y) / unit)
        .let { x.roundToInt() to y.roundToInt() }

/** 转换到直角坐标系 */
fun Pair<Int, Int>.toPixel(unit: Double): Vector2D =
    (pixelTransform * vector2DOf(first, second) * unit).to2D()

/** 在六角格中找到邻居 */
fun Pair<Int, Int>.neighbor(): List<Pair<Int, Int>> {
    val (x, y) = this
    return listOf(x - 1 to y - 1, x to y - 1,
                  x - 1 to y, x + 1 to y,
                  x to y + 1, x + 1 to y + 1)
}
