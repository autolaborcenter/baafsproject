package cn.autolabor.pathfollower

import cn.autolabor.transform.Transformation
import org.mechdancer.algebra.implement.vector.Vector2D

interface PathFollower {
    data class ControlValue(val passCount: Int, val value: Double)

    var path: List<Vector2D>

    operator fun invoke(fromMap: Transformation): Double?
}
