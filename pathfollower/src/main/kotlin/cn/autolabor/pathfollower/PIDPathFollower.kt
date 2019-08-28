package cn.autolabor.pathfollower

import cn.autolabor.transform.Transformation
import org.mechdancer.algebra.function.vector.dot
import org.mechdancer.algebra.function.vector.minus
import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.geometry.angle.adjust
import org.mechdancer.geometry.angle.toAngle
import org.mechdancer.geometry.angle.toRad
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min

class PIDPathFollower(
    val sensor: VirtualLightSensor,
    val ka: Double = 1.0,
    val ki: Double = 0.0,
    val kd: Double = 0.0
) {
    private var i = .0
    private var d = .0

    private var pass = 0

    var path
        get() = pathMarked.map { it.first }
        set(value) {
            pathMarked.clear()
            value.mapTo(pathMarked) { it to 1.0 }
            for (order in 3..5) pathMarked.checkTip(order)
            pass = 0
            i = .0
            d = .0
        }

    var pathMarked = mutableListOf<Pair<Vector2D, Double>>()

    operator fun invoke(fromMap: Transformation): Pair<Double?, Double?> {
        val (passCount, value) =
            sensor(fromMap, path.subList(pass, path.size))
                .takeUnless { (passCount, _) -> passCount < 0 }
            ?: return .0 to null

        pass += passCount
        if (sensor.local.size < 2)
            return null to null

        val tip: Int? =
            pathMarked
                .subList(pass, pass + sensor.local.size)
                .mapIndexed { i, (_, it) -> ItemIndexed(it, i) }
                .filter { it.value < cos(PI / 3) }
                .minBy { it.value }
                ?.index
                ?.let { i ->
                    println("tip = $i")
                    if (i >= 2) i
                    else {
                        val target =
                            (sensor.local[i + 1] - sensor.local[i])
                                .toAngle()
                                .asRadian()
                        val current =
                            (-fromMap)(vector2DOf(1, 0))
                                .to2D()
                                .toAngle()
                                .asRadian()
                        val delta =
                            (target - current)
                                .toRad()
                                .adjust()
                                .value
                        println("Δ = $delta")
                        if (abs(delta) > PI / 6) {
                            pass += i + 1
                            return null to delta
                        }
                        null
                    }
                }

        val actual = tip?.let { sensor(fromMap, sensor.local.subList(0, it + 1)) }
                         ?.second
                     ?: value

        val memory = 1 - ki
        i = memory * i + (1 - memory) * actual
        val dd = actual - d
        d = value
        return 0.05 to ka * (actual + kd * dd + ki * i)
    }

    data class ItemIndexed<T>(val value: T, val index: Int)

    private companion object {
        // 检测尖点
        fun MutableList<Pair<Vector2D, Double>>.checkTip(order: Int) {
            if (size < 2 * order + 1)
                return

            for (i in order until size - order) {
                val (p, value) = get(i)
                val (pf, _) = get(i - order)
                val (pb, _) = get(i + order)
                val v0 = p - pf
                val v1 = pb - p
                set(i, get(i).copy(second = min((v0 dot v1) / (v0.norm() * v1.norm()), value)))
            }
        }
    }
}
