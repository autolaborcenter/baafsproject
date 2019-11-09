package cn.autolabor.pathfollower

import cn.autolabor.business.FollowFailedException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.geometry.angle.Angle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sign

class Commander(
    private val robotOnOdometry: ReceiveChannel<Stamped<Odometry>>,
    private val commandOut: SendChannel<NonOmnidirectional>,
    private val exceptions: SendChannel<ExceptionMessage>,
    directionLimit: Angle,
    private val finish: suspend () -> Unit
) {
    private val turnDirectionRad = directionLimit.asRadian()
    var isEnabled = false

    suspend operator fun invoke(command: FollowCommand) {
        if (command !is FollowCommand.Error) {
            exceptions.send(ExceptionMessage.Recovered(FollowFailedException))
            when (command) {
                is FollowCommand.Follow -> {
                    val (v, w) = command
                    drive(v, w)
                }
                is FollowCommand.Turn   -> {
                    val (w, angle) = command
                    stop()
                    turn(w, angle)
                    stop()
                }
                is FollowCommand.Finish -> {
                    stop()
                    finish()
                }
            }
        } else
            exceptions.send(ExceptionMessage.Occurred(FollowFailedException))
    }

    private suspend fun drive(v: Number, w: Number) {
        if (isEnabled) commandOut.send(Velocity.velocity(v, w))
    }

    private suspend fun stop() {
        commandOut.send(Velocity.velocity(.0, .0))
    }

    private suspend fun turn(omega: Double, angle: Double) {
        val d0 = robotOnOdometry.receive().data.d.asRadian()
        val value = when (turnDirectionRad) {
            in angle..0.0 -> angle + 2 * PI
            in 0.0..angle -> angle - 2 * PI
            else          -> angle
        }
        val delta = abs(value)
        val w = value.sign * omega
        for ((_, pose) in robotOnOdometry) {
            if (abs(pose.d.asRadian() - d0) > delta) break
            drive(0, w)
        }
    }
}
