package com.faselase

import org.mechdancer.geometry.transformation.Transformation

/** 砝石雷达系 */
class FaselaseLidarSet(
    private val map: Map<FaselaseLidar, Transformation>
) {
    val frame
        get() = map.flatMap { (lidar, toRobot) ->
            lidar.frame.map { (_, polar) -> toRobot(polar.toVector2D()) }
        }
}
