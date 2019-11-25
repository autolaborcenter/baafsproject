package cn.autolabor.pathfollower

import cn.autolabor.business.FollowFailedException
import kotlinx.coroutines.channels.SendChannel
import org.mechdancer.common.Velocity
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.exceptions.ExceptionMessage

class Commander(
    private val commandOut: SendChannel<NonOmnidirectional>,
    private val exceptions: SendChannel<ExceptionMessage>,
    private val onFinish: suspend () -> Unit
) {
    var isEnabled = false

    suspend operator fun invoke(command: FollowCommand) {
        if (command !is FollowCommand.Error) {
            exceptions.send(ExceptionMessage.Recovered(FollowFailedException))
            when (command) {
                is FollowCommand.Follow -> {
                    val (v, w) = command
                    if (isEnabled) commandOut.send(Velocity.velocity(v, w))
                }
                is FollowCommand.Finish -> {
                    commandOut.send(Velocity.velocity(.0, .0))
                    onFinish()
                }
            }
        } else
            exceptions.send(ExceptionMessage.Occurred(FollowFailedException))
    }
}
