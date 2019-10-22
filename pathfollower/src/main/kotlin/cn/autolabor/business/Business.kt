package cn.autolabor.business

/** 任务类型/工作状态 */
sealed class Business {
    override fun toString() = this::class.simpleName!!

    /** 循径 */
    data class Follow(val loop: Boolean) : Business()

    /** 录制 */
    object Record : Business()

    /** 空闲 */
    object Idle : Business()
}

