package cn.autolabor.baafs

import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.common.shape.Polygon

// 镜像点列表
fun List<Vector2D>.mirrorY() =
    this + this.map { (x, y) -> Vector2D(+x, -y) }.reversed()

// 机器人外轮廓
val robotOutline = Polygon(
        listOf(Vector2D(+.25, +.08),
               Vector2D(+.12, +.14),
               Vector2D(+.10, +.18),
               Vector2D(+.10, +.26),
               Vector2D(-.10, +.26),
               Vector2D(-.10, +.18),
               Vector2D(-.25, +.18),
               Vector2D(-.47, +.12)
        ).mirrorY())

// 用于过滤的外轮廓
val outlineFilter = Polygon(
        listOf(Vector2D(+.25, +.08),
               Vector2D(+.13, +.14),
               Vector2D(+.13, +.40),
               Vector2D(-.13, +.40),
               Vector2D(-.13, +.18),
               Vector2D(-.25, +.18),
               Vector2D(-.47, +.12)
        ).mirrorY())
