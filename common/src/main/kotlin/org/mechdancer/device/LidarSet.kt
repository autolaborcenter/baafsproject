package org.mechdancer.device

import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.common.Polar
import org.mechdancer.common.Stamped
import org.mechdancer.geometry.transformation.Transformation

/**
 * 雷达系
 *
 * 支持获取最新的一帧
 */
class LidarSet(
    private val map: Map<() -> List<Stamped<Polar>>, Transformation>,
    private val filter: (Vector2D) -> Boolean
) {
    val frame: List<Vector2D>
        get() = map.flatMap { (lidar, toRobot) ->
            lidar().mapNotNull { (_, polar) ->
                toRobot(polar.toVector2D()).to2D().takeIf(filter)
            }
        }
}
