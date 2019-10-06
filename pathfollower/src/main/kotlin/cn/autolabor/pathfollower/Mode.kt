package cn.autolabor.pathfollower

import cn.autolabor.pathfollower.Mode.Follow

// 任务类型/工作状态
sealed class Mode {
    override fun toString() = this::class.simpleName!!

    data class Follow(val loop: Boolean) : Mode()
    object Record : Mode()
    object Idle : Mode()
}
