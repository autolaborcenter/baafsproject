package cn.autolabor.pm1

import org.mechdancer.geometry.angle.Angle

/** 机器人动作 */
sealed class Action {
    /** 空闲状态 */
    object Idle : Action()

    /** 等待到 [target] 时刻 */
    data class Wait(val target: Long) : Action()

    /** 行驶动作 */
    sealed class Drive : Action() {
        abstract val v: Double
        abstract val w: Double

        /** 自由行驶 */
        data class Free(
            override val v: Double,
            override val w: Double,
            val acted: Boolean = false
        ) : Drive()

        /** 旋转到 [target] 角度 */
        data class Turn(
            override val w: Double,
            val target: Angle
        ) : Drive() {
            override val v = .0
        }
    }
}
