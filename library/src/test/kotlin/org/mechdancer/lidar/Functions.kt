package org.mechdancer.lidar

import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.common.Odometry
import org.mechdancer.common.shape.Circle
import org.mechdancer.common.shape.Polygon
import org.mechdancer.common.toTransformation

internal fun Polygon.transform(pose: Odometry): Polygon {
    val tf = pose.toTransformation()
    return Polygon(vertex.map { tf(it).to2D() })
}

// 镜像点列表
private fun List<Vector2D>.mirror() =
    this + this.map { (x, y) -> vector2DOf(-x, y) }.reversed()

// 机器人内的遮挡
internal val cover =
    listOf(Circle(.07).sample().transform(Odometry.pose(-.36)),
           listOf(vector2DOf(-.10, +.26),
                  vector2DOf(-.10, +.20),
                  vector2DOf(-.05, +.20),
                  vector2DOf(-.05, -.20),
                  vector2DOf(-.10, -.20),
                  vector2DOf(-.10, -.26)
           ).mirror().let(::Polygon))
