package cn.autolabor.pathfollower

import cn.autolabor.business.GlobalPath
import cn.autolabor.pathfollower.FollowCommand.*
import org.mechdancer.DebugTemporary
import org.mechdancer.DebugTemporary.Operation.DELETE
import org.mechdancer.DebugTemporary.Operation.REDUCE
import org.mechdancer.SimpleLogger
import org.mechdancer.algebra.function.vector.dot
import org.mechdancer.algebra.function.vector.plus
import org.mechdancer.algebra.function.vector.times
import org.mechdancer.common.Odometry
import org.mechdancer.common.filters.Filter
import org.mechdancer.geometry.angle.Angle
import org.mechdancer.geometry.angle.adjust
import org.mechdancer.geometry.angle.toAngle
import org.mechdancer.geometry.angle.toVector
import kotlin.math.*

/**
 * 循径控制器
 *
 * * 参数
 *   * 主传感器 [sensor], 包括形状和位置
 *   * 主控制器 [controller]
 *   * 两点方向差大于 [minTipAngle] 判定为尖点
 *   * 在尖点处目标转角大于 [minTurnAngle] 触发转动
 *   * 最大线速度 [maxLinearSpeed]
 *   * 最大角速度 [maxAngularSpeed]
 */
class VirtualLightSensorPathFollower
internal constructor(
    val global: GlobalPath,
    @DebugTemporary(REDUCE)
    val sensor: VirtualLightSensor,
    private val controller: Filter<Double, Double>,
    minTipAngle: Angle,
    minTurnAngle: Angle,
    internal val maxLinearSpeed: Double,
    maxAngularSpeed: Angle
) {
    private var pre = .0

    private val cosMinTip = cos(minTipAngle.asRadian())
    private val minTurnRad = minTurnAngle.asRadian()
    internal val maxOmegaRad = maxAngularSpeed.asRadian()

    @DebugTemporary(DELETE)
    private val logger = SimpleLogger("firstOfLocal")

    @DebugTemporary(DELETE)
    var tip = Odometry()
        private set

    /** 计算控制量 */
    operator fun invoke(pose: Odometry): FollowCommand {
        val bright = sensor.shine(global[pose])
        // 特殊情况提前退出
        var pn = bright.firstOrNull()
                     ?.also { (p, d) -> logger.log(p.x, p.y, d.asRadian()) }
                 ?: return when {
                     global.progress == 1.0 -> Finish
                     abs(pre) > minTurnRad  -> Turn(pre)
                     else                   -> Error
                 }
        // 查找尖点
        val (tip, i) =
            bright
                .drop(1)
                .asSequence()
                .mapIndexed { i, item -> item to i }
                .firstOrNull { (it, _) ->
                    val `pn-1` = pn
                    pn = it
                    pn.d.toVector() dot `pn-1`.d.toVector() < cosMinTip
                }
            ?: (bright.last() to bright.lastIndex)
        @DebugTemporary(DELETE)
        this.tip = tip
        // 处理尖点
        when {
            i in 1..4 -> pre = tip.d.adjust().asRadian()
            i > 4     -> pre = .0
            else      -> {
                global += i + 1
                val target = (tip.p + tip.d.toVector() * 0.2).toAngle().adjust().asRadian()
                if (abs(target) > minTurnRad) return Turn(target)
            }
        }
        // 计算控制量
        return Follow(v = maxLinearSpeed,
                      w = controller
                          .update(new = sensor(bright.take(i + 1)))
                          .run { sign * min(maxOmegaRad, absoluteValue) })
    }
}
