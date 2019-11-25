package cn.autolabor.baafs

import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.common.shape.AnalyticalShape
import org.mechdancer.common.shape.Polygon
import org.mechdancer.common.shape.Shape

// 镜像点列表
fun List<Vector2D>.mirrorY() =
    this + this.map { (x, y) -> vector2DOf(+x, -y) }.reversed()

// 机器人外轮廓
val robotOutline = Polygon(
        listOf(vector2DOf(+.25, +.08),
               vector2DOf(+.12, +.14),
               vector2DOf(+.10, +.18),
               vector2DOf(+.10, +.26),
               vector2DOf(-.10, +.26),
               vector2DOf(-.10, +.18),
               vector2DOf(-.25, +.18),
               vector2DOf(-.47, +.12)
        ).mirrorY())

// 用于过滤的外轮廓
val outlineFilter = Polygon(
        listOf(vector2DOf(+.25, +.08),
               vector2DOf(+.13, +.14),
               vector2DOf(+.13, +.40),
               vector2DOf(-.13, +.40),
               vector2DOf(-.13, +.18),
               vector2DOf(-.25, +.18),
               vector2DOf(-.47, +.12)
        ).mirrorY())

fun Shape.toPolygon() =
    when (this) {
        is Polygon         -> this
        is AnalyticalShape -> this.sample()
        else               -> throw TypeCastException()
    }
