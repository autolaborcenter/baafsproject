package cn.autolabor.pathfollower.algorithm

import cn.autolabor.pathfollower.algorithm.FollowCommand.*
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
import kotlin.math.*

/**
 * 使用 [sensor] 从路径生成误差信号的循径控制器
 */
class VirtualLightSensorPathFollower(
    val sensor: VirtualLightSensor,
    private val controller: Controller = Controller.unit,
    private val minTipAngle: Double,
    private val minTurnAngle: Double,
    private val maxJumpCount: Int,
    val maxLinearSpeed: Double,
    val maxAngularSpeed: Double
) {
    private var pass = 0
    private var path = listOf<Pair<Odometry, Double>>()
    private var pre = .0

    @Temporary(DELETE)
    var tip = Odometry()
        private set

    fun setPath(path: List<Odometry>) {
        this.path = listOf(path.first() to 2.0) +
                    (1 until path.size).map { i ->
                        val v0 = path[i - 1].d.toVector()
                        val v1 = path[i].d.toVector()
                        path[i] to (v0 dot v1)
                    }
        // 重置状态
        pass = 0
        controller.clear()
    }

    private fun subPath(begin: Int, end: Int) =
        path.subList(begin, end).map(Pair<Odometry, *>::first)

    operator fun invoke(pose: Odometry): FollowCommand {
        val (begin, end) =
            when (pass) {
                0    -> path.size
                else -> min(pass + maxJumpCount, path.size)
            }.let { limit ->
                val range = sensor.findLocal(pose, subPath(pass, limit))
                (pass + range.first) to (pass + range.last)
            }
        // 特殊情况提前退出
        when {
            begin > end                           ->
                return when {
                    abs(pre) > minTurnAngle -> Turn(pre)
                    else                    -> Error
                }
            begin == end && end == path.lastIndex ->
                return Finish
        }
        // 丢弃通过的路径
        val next = path.subList(begin, min(end + 2, path.size))
        pass = begin
        // 利用缓存识别尖点
        return (next.asSequence()
                    .mapIndexed { i, item -> item to i }
                    .firstOrNull { (it, _) -> it.second < cos(minTipAngle) }
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
                        if (abs(delta) > minTurnAngle) return Turn(delta)
                    }
                }
            }.second
            .let { sensor(pose, subPath(pass, pass + it)) }
            .let {
                Follow(v = maxLinearSpeed,
                       w = controller(input = it).run { sign * min(maxAngularSpeed, absoluteValue) })
            }
    }
}

