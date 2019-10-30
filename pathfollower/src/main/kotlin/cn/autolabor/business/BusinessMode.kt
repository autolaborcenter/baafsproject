package cn.autolabor.business

/** 任务类型/工作状态 */
sealed class BusinessMode {
    override fun toString() = this::class.simpleName!!

    /** 循径 */
    data class Follow(val loop: Boolean) : BusinessMode()

    /** 录制 */
    object Record : BusinessMode()

    /** 空闲 */
    object Idle : BusinessMode()
}

