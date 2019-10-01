package cn.autolabor.pathfollower

sealed class FollowCommand {
    data class Follow(val v: Double, val w: Double) : FollowCommand()
    data class Turn(val angle: Double) : FollowCommand()
    object Error : FollowCommand() {
        override fun toString() = "Error"
    }

    object Finish : FollowCommand() {
        override fun toString() = "Finish"
    }
}
