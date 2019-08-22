package cn.autolabor.pathfollower

import cn.autolabor.utilities.Odometry
import org.mechdancer.algebra.implement.vector.Vector2D

class VirtualLightSensor(
    private val delta: Odometry,
    private val range: Shape
) {
    operator fun invoke(state: Odometry, path: List<Vector2D>) {
        val center = state plusDelta delta
    }
}
