package cn.autolabor.pm1

import cn.autolabor.Odometry
import cn.autolabor.pm1.Action.Drive.Free
import cn.autolabor.pm1.Action.Drive.Turn
import cn.autolabor.pm1.Action.Idle
import cn.autolabor.pm1.Action.Wait
import org.mechdancer.geometry.angle.Angle
import kotlin.math.abs
import kotlin.math.sign

/** 底盘控制器 */
class ActionController {
    /** 当前动作 */
    private var action: Action = Idle

    /** 等待到时刻 */
    fun waitUntil(target: Long) {
        action = Wait(target)
    }

    /** 控制自由行驶 */
    fun drive(v: Double, w: Double) {
        action = if (abs(v) < 0.01 && abs(w) < 0.01) Idle else Free(v, w)
    }

    /** 控制原地转身 */
    fun turn(w: Double, target: Angle) {
        action = if (abs(w) < 0.01) Idle else Turn(w, target)
    }

    /** 更新状态 */
    operator fun invoke(pose: Odometry): Action {
        action = when (val current = action) {
            Idle    -> Idle
            is Wait -> current
                           .takeIf { (target) -> System.currentTimeMillis() < target }
                       ?: Idle
            is Free -> current
                           .takeUnless { it.acted }
                           ?.also { action = current.copy(acted = true) }
                       ?: Idle
            is Turn -> {
                val (w, target) = current
                val theta = pose.d.asRadian()
                if ((target.asRadian() - theta).sign != w.sign)
                    Idle
                else
                    current
            }
        }
        return action
    }
}
