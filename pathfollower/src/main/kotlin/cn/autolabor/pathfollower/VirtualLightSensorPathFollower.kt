package cn.autolabor.pathfollower

import cn.autolabor.pathfollower.VirtualLightSensorPathFollower.FollowCommand.Follow
import cn.autolabor.pathfollower.VirtualLightSensorPathFollower.FollowCommand.Turn
import org.mechdancer.Temporary
import org.mechdancer.Temporary.Operation.DELETE
import org.mechdancer.algebra.function.vector.dot
import org.mechdancer.algebra.function.vector.minus
import org.mechdancer.algebra.function.vector.plus
import org.mechdancer.algebra.function.vector.times
import org.mechdancer.common.Odometry
import org.mechdancer.geometry.angle.adjust
import org.mechdancer.geometry.angle.toAngle
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
    private val tipJudge: Double = PI / 3,
    private val maxJumpCount: Int = 20
) {
    private var pass = 0
    private var pathMarked = listOf<Pair<Odometry, Double>>()
    private var pre = .0

    @Temporary(DELETE)
    var tip = Odometry()
        private set

    /** 读写工作路径 */
    var path = listOf<Odometry>()
        set(value) {
            // 存储
            field = value
            pathMarked = listOf(value.first() to 2.0) +
                         (1 until value.size).map { i ->
                             val v0 = value[i - 1].d.toVector()
                             val v1 = value[i].d.toVector()
                             value[i] to (v0 dot v1)
                         }
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
        val (begin, end) =
            when (pass) {
                0    -> path.size
                else -> min(pass + maxJumpCount, path.size)
            }.let { limit ->
                val range = sensor.findLocal(pose, path.subList(pass, limit))
                (pass + range.first) to (pass + range.last)
            }
        // 特殊情况提前退出
        when {
            begin > end                           ->
                return when {
                    abs(pre) > tipJudge / 2 -> Turn(pre)
                    else                    -> FollowCommand.Error
                }
            begin == end && end == path.lastIndex ->
                return FollowCommand.Finish
        }
        // 丢弃通过的路径
        val next = pathMarked.subList(begin, min(path.size, end + 2))
        pass = begin
        // 利用缓存识别尖点
        return (next.asSequence()
                    .mapIndexed { i, item -> item to i }
                    .firstOrNull { (it, _) -> it.second < cos(tipJudge) }
                ?: next.last() to next.lastIndex)
            // 处理尖点
            .also { (item, i) ->
                val (tip, _) = item
                this.tip = tip
                when {
                    i in 2..5 -> {
                        val target = tip.d.asRadian()
                        val current = pose.d.asRadian()
                        pre = (target - current).toRad().adjust().asRadian()
                    }
                    i > 5     -> pre = .0
                    else      -> {
                        ++pass
                        val target = (tip.p + tip.d.toVector() * 0.2 - pose.p).toAngle().asRadian()
                        val current = pose.d.asRadian()
                        val delta = (target - current).toRad().adjust().asRadian()
                        if (abs(delta) > tipJudge / 2) return Turn(delta)
                    }
                }
            }.second
            .let { sensor(pose, path.subList(pass, pass + it)) }
            .let { Follow(0.1, controller(input = it)) }
    }
}
