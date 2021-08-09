package org.mechdancer.action

import cn.autolabor.pm1.model.ControlVariable.Physical
import org.mechdancer.algebra.function.vector.dot
import org.mechdancer.algebra.function.vector.plus
import org.mechdancer.algebra.function.vector.times
import org.mechdancer.common.Odometry
import org.mechdancer.core.ActionPlanner
import org.mechdancer.core.LocalPath
import org.mechdancer.geometry.angle.*
import org.mechdancer.paint
import org.mechdancer.paintPoses
import org.mechdancer.remote.presets.RemoteHub
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sign

/**
 * 循径控制器
 *
 * * 参数
 *   * 主传感器 [sensor], 包括形状和位置
 *   * 两点方向差大于 [minTipAngle] 判定为尖点
 *   * 在尖点处目标转角大于 [minTurnAngle] 触发转动
 *   * 转向分界线 [turnThreshold]
 *   * 最大线速度 [maxSpeed]
 */
class VirtualLightSensorPathFollower(
    private val sensor: VirtualLightSensor,
    minTipAngle: Angle,
    minTurnAngle: Angle,
    turnThreshold: Angle,
    private val maxSpeed: Double
) : ActionPlanner<Physical> {
    private var dir = 0
    private var turning = false

    private val cosMinTip = cos(minTipAngle.asRadian())
    private val minTurnRad = minTurnAngle.asRadian()
    private val turnThresholdRad = turnThreshold.asRadian()

    private companion object {
        const val PRE_TURN_COUNT = 4
    }

    var painter: RemoteHub? = null

    override suspend fun plan(path: LocalPath) =
        when (path) {
            LocalPath.Finish     -> Physical.static
            LocalPath.Failure    -> null
            is LocalPath.KeyPose -> {
                val d = path.pose.d
                if (abs(d.asDegree()) < 10)
                    Physical.static
                else {
                    dir = d.asRadian().sign.toInt()
                    turn()
                }
            }
            is LocalPath.Path    -> invoke(path.path)
        }

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

    private fun turn(): Physical {
        turning = true
        return Physical(maxSpeed, (-dir * PI / 2).toRad())
    }

    private fun invoke(path: Sequence<Odometry>): Physical? {
        // 光感采样
        val bright = sensor.shine(path)
        if (turning && bright.size < PRE_TURN_COUNT) return turn()
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
            i > PRE_TURN_COUNT     -> dir = 0
            i in 1..PRE_TURN_COUNT -> calculateDir(tip)
            else                   -> if (calculateDir(tip)) return turn()
        }
        turning = false
        val light = sensor(bright.take(i + 1))
        sensor.area?.let {
            painter?.paint("R 传感器区域", it)
            painter?.paintPoses("R 尖点", listOf(tip))
        }
        // 计算控制量
        return Physical(maxSpeed, (-PI / 2 * light).toRad())
    }
}
