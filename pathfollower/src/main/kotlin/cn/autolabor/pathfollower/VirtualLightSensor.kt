package cn.autolabor.pathfollower

import cn.autolabor.transform.Transformation
import org.mechdancer.algebra.core.Vector
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.to2D

class VirtualLightSensor(
    private val sensorFromBaseLink: Transformation,
    private val range: Shape
) {
    operator fun invoke(
        baseLinkFromMap: Transformation,
        path: List<Vector2D>
    ) {
        val sensorFromMap = sensorFromBaseLink * baseLinkFromMap

        path.asSequence()
            .map(sensorFromMap::invoke)
            .map(Vector::to2D)
    }
}
