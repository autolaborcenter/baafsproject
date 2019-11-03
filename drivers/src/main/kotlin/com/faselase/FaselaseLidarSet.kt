package com.faselase

import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.device.LidarSet
import org.mechdancer.geometry.transformation.Transformation

class FaselaseLidarSet(
    private val map: Map<FaselaseLidar, Transformation>,
    private val filter: (Vector2D) -> Boolean
) : LidarSet {
    override val frame: List<Vector2D>
        get() = map.flatMap { (lidar, toRobot) ->
            lidar.frame.mapNotNull { (_, polar) ->
                toRobot(polar.toVector2D()).to2D().takeIf(filter)
            }
        }
}
