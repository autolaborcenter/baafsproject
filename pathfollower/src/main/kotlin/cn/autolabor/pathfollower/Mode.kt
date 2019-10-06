package cn.autolabor.pathfollower

// 任务类型/工作状态
sealed class Mode {
    object Record : Mode()
    data class Follow(val loop: Boolean) : Mode()
    object Idle : Mode()
}
