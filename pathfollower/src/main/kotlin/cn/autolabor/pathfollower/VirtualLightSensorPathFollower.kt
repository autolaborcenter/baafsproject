package cn.autolabor.pathfollower

import cn.autolabor.pathfollower.VirtualLightSensorPathFollower.FollowCommand.Follow
import cn.autolabor.pathfollower.VirtualLightSensorPathFollower.FollowCommand.Turn
import org.mechdancer.algebra.function.vector.dot
import org.mechdancer.algebra.function.vector.minus
import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.common.Odometry
import org.mechdancer.geometry.angle.adjust
import org.mechdancer.geometry.angle.toAngle
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.geometry.angle.toVector
import org.mechdancer.geometry.transformation.Transformation
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
    private val pathMarked = mutableListOf<Pair<Odometry, Double>>()

    /** 读写工作路径 */
    var path = listOf<Odometry>()
        set(value) {
            // 存储
            field = value
            pathMarked.clear()
            for (i in 0 until value.lastIndex) {
                val v0 = value[i].d.toVector()
                val v1 = value[i + 1].d.toVector()
                pathMarked.add(value[i] to (v0 dot v1))
            }
            pathMarked.add(value.last() to 2.0)
            // 重置状态
            pass = 0
            controller.clear()
        }

    sealed class FollowCommand {
        data class Follow(val v: Double, val w: Double) : FollowCommand()
        data class Turn(val angle: Double) : FollowCommand()
        object Error : FollowCommand() {
            override fun toString() = "Error"
        }

        object Finish : FollowCommand() {
            override fun toString() = "Finish"
        }
    }

    operator fun invoke(fromMap: Transformation): FollowCommand {
        // 第一次调用传感器
        val (passCount, value) =
            sensor(fromMap, pathMarked.subList(pass, path.size).map(Pair<Odometry, *>::first))
                .takeUnless { (passCount, _) -> passCount < 0 }
            ?: return FollowCommand.Error
        // 判断路径终点
        listOf(sensor.local.last(), pathMarked.last().first)
            .map { fromMap(it.p).norm() }
            .all { it < destinationJudge }
            .let { if (it) return FollowCommand.Finish }
        // 丢弃通过的路径
        pass += passCount
        return pathMarked
            // 利用缓存识别尖点
            .subList(pass, pass + sensor.local.size)
            .mapIndexed { i, (_, it) -> it to i }
            .filter { (it, _) -> it < cos(tipJudge) }
            .minBy { (it, _) -> it }
            ?.second
            // 处理尖点
            ?.let { i ->
                if (i >= 2) i
                else {
                    val target =
                        sensor.local
                            .let { (it[min(i + 3, it.lastIndex)].p - it[i].p) }
                            .toAngle().asRadian()
                    val current =
                        -fromMap
                            .invokeLinear(vector2DOf(1, 0))
                            .to2D()
                            .toAngle().asRadian()
                    val delta = (target - current).toRad().adjust().asRadian()
                    if (abs(delta) > tipJudge / 2) {
                        pass += i
                        return Turn(delta)
                    }
                    null
                }
            }
            ?.let { sensor(fromMap, sensor.local.subList(0, it + 1)) }
            ?.second
            .let { Follow(0.1, controller(input = it ?: value)) }
    }
}
