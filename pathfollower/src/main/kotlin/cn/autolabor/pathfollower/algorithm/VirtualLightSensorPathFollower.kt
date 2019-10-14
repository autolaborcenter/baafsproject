package cn.autolabor.pathfollower.algorithm

import cn.autolabor.pathfollower.algorithm.FollowCommand.*
import org.mechdancer.DebugTemporary
import org.mechdancer.DebugTemporary.Operation.DELETE
import org.mechdancer.DebugTemporary.Operation.REDUCE
import org.mechdancer.algebra.function.vector.dot
import org.mechdancer.algebra.function.vector.minus
import org.mechdancer.algebra.function.vector.plus
import org.mechdancer.algebra.function.vector.times
import org.mechdancer.common.Odometry
import org.mechdancer.geometry.angle.*
import kotlin.math.*

/**
 * 循径控制器
 *
 * * 参数
 *   * 主传感器 [sensor], 包括形状和位置
 *   * 主控制器 [controller]
 *   * 两点方向差大于 [minTipAngle] 判定为尖点
 *   * 在尖点处目标转角大于 [minTurnAngle] 触发转动
 *   * 一次控制最多向前查找 [maxJumpCount] 点数,此范围内无局部路径则判定为异常状态
 *   * 最大线速度 [maxLinearSpeed]
 *   * 最大角速度 [maxAngularSpeed]
 */
class VirtualLightSensorPathFollower
internal constructor(
    @DebugTemporary(REDUCE)
    val sensor: VirtualLightSensor,
    private val controller: Controller = Controller.unit,
    minTipAngle: Angle,
    minTurnAngle: Angle,
    private val maxJumpCount: Int,
    internal val maxLinearSpeed: Double,
    maxAngularSpeed: Angle
) {
    private var pass = 0
    private var path = listOf<Pair<Odometry, Double>>()
    private var pre = .0

    private val cosMinTip = cos(minTipAngle.asRadian())
    private val minTurnRad = minTurnAngle.asRadian()
    internal val maxOmegaRad = maxAngularSpeed.asRadian()

    @DebugTemporary(DELETE)
    var tip = Odometry()
        private set

    /** 设置目标路径 */
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

    /** 查询/修改进度 */
    var progress: Double
        get() = if (path.isEmpty()) 1.0 else pass.toDouble() / path.size
        set(value) {
            require(value in 0.0..1.0) { "progress should be in [0, 1]" }
            pass = (value * path.size).toInt()
            searchAll = true
        }

    // 允许搜索全部路径
    private var searchAll = false

    operator fun invoke(pose: Odometry): FollowCommand {
        if (pass == 0) searchAll = true
        val (begin, end) =
            when {
                searchAll -> path.size
                else      -> min(pass + maxJumpCount, path.size)
            }.let { limit ->
                val range = sensor.findLocal(pose, subPath(pass, limit))
                (pass + range.first) to (pass + range.last)
            }
        searchAll = false
        // 特殊情况提前退出
        when {
            begin > end                           ->
                return when {
                    abs(pre) > minTurnRad -> Turn(pre)
                    else                  -> Error
                }
            begin == end && end == path.lastIndex ->
                return Finish.also { pass = path.size }
        }
        // 丢弃通过的路径
        val next = path.subList(begin, min(end + 2, path.size))
        pass = begin
        // 利用缓存识别尖点
        return (next.asSequence()
                    .mapIndexed { i, item -> item to i }
                    .firstOrNull { (it, _) -> it.second < cosMinTip }
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
                        if (abs(delta) > minTurnRad) return Turn(delta)
                    }
                }
            }.second
            .let { sensor(pose, subPath(pass, pass + it)) }
            .let {
                Follow(v = maxLinearSpeed,
                       w = controller(input = it).run { sign * min(maxOmegaRad, absoluteValue) })
            }
    }

    private fun subPath(begin: Int, end: Int) =
        path.subList(begin, end).map(Pair<Odometry, *>::first)
}
