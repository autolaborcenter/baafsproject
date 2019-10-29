package com.faselase

import org.mechdancer.algebra.core.Vector
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.geometry.transformation.Transformation

class FaselaseLidarSet(
    private val map: Map<FaselaseLidar, Transformation>,
    private val filter: (Vector2D) -> Boolean
) {
    val frame: List<Vector2D>
        get() = map
            .asSequence()
            .flatMap { (lidar, toRobot) ->
                lidar.frame
                    .asSequence()
                    .map { (_, polar) -> toRobot(polar.toVector2D()) }
            }
            .map(Vector::to2D)
            .filter(filter)
            .toList()
}
