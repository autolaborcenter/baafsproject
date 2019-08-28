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

/**
 * 使用 [sensor] 从路径生成误差信号的循径控制器
 */
class VirtualLightSensorPathFollower(
    val sensor: VirtualLightSensor,
    private val controller: Controller = Controller.unit,
    private val tipOrderRange: IntRange = 3..3,
    private val tipJudge: Double = PI / 3,
    private val destinationJudge: Double = 0.1
) {
    private var pass = 0
    private val pathMarked = mutableListOf<Pair<Vector2D, Double>>()

    /** 读写工作路径 */
    var path = listOf<Vector2D>()
        set(value) {
            // 存储
            field = value
            // 尖点检测
            pathMarked.clear()
            value.mapTo(pathMarked) { it to 1.0 }
            for (order in tipOrderRange) pathMarked.checkTip(order)
            // 重置状态
            pass = 0
            controller.clear()
        }

    operator fun invoke(fromMap: Transformation): Pair<Double?, Double?> {
        // 第一次调用传感器
        val (passCount, value) =
            sensor(fromMap, path.subList(pass, path.size))
                .takeUnless { (passCount, _) -> passCount < 0 }
            ?: return .0 to null

        pass += passCount
        if (sensor.local.size < 2)
            return null to null
        // 判断路径终点
        listOf(sensor.local.last(), path.last())
            .asSequence()
            .map { fromMap(it).norm() }
            .all { it < destinationJudge }
            .let { if (it) return .0 to null }
        // 处理尖点
        val tip: Int? =
            pathMarked
                .subList(pass, pass + sensor.local.size)
                .mapIndexed { i, (_, it) -> ItemIndexed(it, i) }
                .filter { it.value < cos(tipJudge) }
                .minBy { it.value }
                ?.index
                ?.let { i ->
                    if (i >= 2) i
                    else {
                        val target =
                            (sensor.local[i + 1] - sensor.local[i])
                                .toAngle()
                                .asRadian()
                        val toMap = -fromMap
                        val current =
                            (toMap(vector2DOf(1, 0)) - toMap(vector2DOf(0, 0)))
                                .to2D()
                                .toAngle()
                                .asRadian()
                        val delta =
                            (target - current)
                                .toRad()
                                .adjust()
                                .asRadian()
                        if (abs(delta) > tipJudge / 2) {
                            pass += i
                            return null to delta
                        }
                        null
                    }
                }

        val actual = tip?.let { sensor(fromMap, sensor.local.subList(0, it + 1)) }
                         ?.second
                     ?: value

        return 0.1 to controller(input = actual)
    }

    private data class ItemIndexed<T>(val value: T, val index: Int)

    private companion object {
        // 检测尖点
        fun MutableList<Pair<Vector2D, Double>>.checkTip(order: Int) {
            if (size > 2 * order)
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
