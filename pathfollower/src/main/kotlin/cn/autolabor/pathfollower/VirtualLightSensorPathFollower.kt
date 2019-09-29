package cn.autolabor.pathfollower

import cn.autolabor.pathfollower.VirtualLightSensorPathFollower.FollowCommand.Follow
import cn.autolabor.pathfollower.VirtualLightSensorPathFollower.FollowCommand.Turn
import org.mechdancer.algebra.function.vector.dot
import org.mechdancer.algebra.function.vector.minus
import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.common.Odometry
import org.mechdancer.geometry.angle.adjust
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.geometry.angle.toVector
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
    private var pre = .0

    /** 读写工作路径 */
    var path = listOf<Odometry>()
        set(value) {
            // 存储
            field = value
            pathMarked.clear()
            for (i in 1 until value.size) {
                val v0 = value[i - 1].d.toVector()
                val v1 = value[i].d.toVector()
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

    operator fun invoke(pose: Odometry): FollowCommand {
        val limit = if (pass == 0) path.size else min(pass + 40, path.size)
        val localRange = sensor.findLocal(pose, path.subList(pass, limit))
        if (localRange.isEmpty()) return if (abs(pre) > tipJudge / 2) Turn(pre) else FollowCommand.Error
        // 判断路径终点
        if (pass + localRange.last == path.lastIndex && (pose.p - path.last().p).norm() < destinationJudge)
            return FollowCommand.Finish
        // 丢弃通过的路径
        val next = pathMarked.subList(pass + localRange.first, pass + localRange.last + 1)
        pass += localRange.first
        // 利用缓存识别尖点
        return (next.asSequence()
                    .mapIndexed { i, item -> item to i }
                    .firstOrNull { (it, _) -> it.second < cos(tipJudge) }
                ?: next.last() to next.lastIndex)
            // 处理尖点
            .also { (item, i) ->
                when {
                    i in 1..4 -> {
                        val target = item.first.d.asRadian()
                        val current = pose.d.asRadian()
                        pre = (target - current).toRad().adjust().asRadian()
                    }
                    i > 4     -> pre = .0
                    else      -> {
                        ++pass
                        val target = item.first.d.asRadian()
                        val current = pose.d.asRadian()
                        val delta = (target - current).toRad().adjust().asRadian()
                        if (abs(delta) > tipJudge / 2) return Turn(delta)
                    }
                }
            }.second
            .let { sensor(pose, path.subList(pass, pass + it + 1)) }
            .let { Follow(0.1, controller(input = it)) }
    }
}
