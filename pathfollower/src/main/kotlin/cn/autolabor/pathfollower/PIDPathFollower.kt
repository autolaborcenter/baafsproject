package cn.autolabor.pathfollower

import cn.autolabor.transform.Transformation
import org.mechdancer.algebra.implement.vector.Vector2D
import kotlin.math.abs

class PIDPathFollower(
    val sensor: VirtualLightSensor,
    val ka: Double = 1.0,
    val ki: Double = 0.0,
    val kd: Double = 0.0
) : PathFollower {
    private var last: Double? = null
    private var i = .0
    private var d = .0

    private var pass = 0

    override var path = listOf<Vector2D>()
        set(value) {
            field = value
            last = null
            pass = 0
            i = .0
            d = .0
        }

    override fun invoke(
        fromMap: Transformation
    ) = runCatching {
        sensor(fromMap, path.subList(pass, path.size))
    }
        .getOrNull()
        ?.takeIf { (passCount, value) ->
            pass = passCount
            false != last?.let {
                val delta = abs(it - value)
                delta < .6
            }
        }
        ?.value
        ?.also { last = it }
        ?.let {
            val memory = 1 - ki
            i = memory * i + (1 - memory) * it
            val dd = it - d
            d = it
            ka * (it + kd * dd + ki * i)
        }
}
