package cn.autolabor.pathfollower

/** 控制指令 */
sealed class FollowCommand {
    override fun toString() = javaClass.simpleName!!

    /** 跟随 */
    data class Follow(val v: Double, val w: Double) : FollowCommand()

    /** 原地转 */
    data class Turn(val angle: Double) : FollowCommand()

    /** 循径失败 */
    object Error : FollowCommand()

    /** 循径完成 */
    object Finish : FollowCommand()
}
