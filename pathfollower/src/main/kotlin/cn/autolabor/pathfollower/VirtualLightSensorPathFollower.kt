package cn.autolabor.pathfollower

import org.mechdancer.algebra.function.vector.dot
import org.mechdancer.algebra.function.vector.plus
import org.mechdancer.algebra.function.vector.times
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.common.filters.Filter
import org.mechdancer.geometry.angle.Angle
import org.mechdancer.geometry.angle.adjust
import org.mechdancer.geometry.angle.toAngle
import org.mechdancer.geometry.angle.toVector
import org.mechdancer.paint
import org.mechdancer.paintPoses
import org.mechdancer.remote.presets.RemoteHub
import kotlin.math.*

/**
 * 循径控制器
 *
 * * 参数
 *   * 主传感器 [sensor], 包括形状和位置
 *   * 主控制器 [controller]
 *   * 两点方向差大于 [minTipAngle] 判定为尖点
 *   * 在尖点处目标转角大于 [minTurnAngle] 触发转动
 *   * 转向分界线 [turnThreshold]
 *   * 最大线速度 [maxLinearSpeed]
 *   * 最大角速度 [maxAngularSpeed]
 */
class VirtualLightSensorPathFollower(
    private val sensor: VirtualLightSensor,
    private val controller: Filter<Double, Double>,
    minTipAngle: Angle,
    minTurnAngle: Angle,
    turnThreshold: Angle,
    private val maxLinearSpeed: Double,
    maxAngularSpeed: Angle,
    private val kLinearSpeed: Double,

    private val painter: RemoteHub?
) {
    private var dir = 0
    private var turning = false

    private val cosMinTip = cos(minTipAngle.asRadian())
    private val minTurnRad = minTurnAngle.asRadian()
    private val maxOmegaRad = maxAngularSpeed.asRadian()
    private val turnThresholdRad = turnThreshold.asRadian()

    private var t0 = 0L

    private fun calculateDir(tip: Odometry): Boolean {
        val target = (tip.p + tip.d.toVector() * 0.2).toAngle().adjust().asRadian()
        val turn = abs(target) > minTurnRad
        dir = if (turn)
            when (turnThresholdRad) {
                in target..0.0 -> target + 2 * PI
                in 0.0..target -> target - 2 * PI
                else           -> target
            }.sign.toInt() else 0
        return turn
    }

    private fun turn(): NonOmnidirectional {
        turning = true
        return NonOmnidirectional(.0, dir * maxOmegaRad)
    }

    private val turnCount = 4

    /** 计算控制量 */
    operator fun invoke(local: Stamped<Sequence<Odometry>>): NonOmnidirectional? {
        if (t0 == 0L) t0 = local.time
        // 光感采样
        val bright = sensor.shine(local.data)
        if (turning && bright.size < turnCount) return turn()
        // 处理异常
        var pn =
            bright.firstOrNull()
            ?: return when {
                dir != 0 -> turn()
                else     -> null
            }
        // 查找尖点
        val (tip, i) =
            bright
                .asSequence()
                .drop(1)
                .mapIndexed { i, item -> item to i }
                .firstOrNull { (it, _) ->
                    val `pn-1` = pn
                    pn = it
                    pn.d.toVector() dot `pn-1`.d.toVector() < cosMinTip
                }
            ?: (bright.last() to bright.lastIndex)
        // 处理尖点
        when {
            i > turnCount     -> dir = 0
            i in 1..turnCount -> calculateDir(tip)
            else              -> if (calculateDir(tip)) return turn()
        }
        turning = false
        val light = sensor(bright.take(i + 1))
        sensor.area?.let {
            painter?.paint("R 传感器区域", it)
            painter?.paintPoses("R 尖点", listOf(tip))
        }
        painter?.paint("控制器输入", (local.time - t0).toDouble(), light)
        // 计算控制量
        return NonOmnidirectional(
                v = maxLinearSpeed * min(1.0, kLinearSpeed * (1 - abs(light))),
                w = controller
                    .update(new = light, time = local.time)
                    .run { sign * min(maxOmegaRad, absoluteValue) })
    }
}
