package cn.autolabor.pathfollower

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.mechdancer.BuilderDslMarker
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.geometry.angle.Angle
import org.mechdancer.geometry.angle.toDegree

@BuilderDslMarker
class CommanderBuilderDsl
private constructor() {
    var directionLimit: Angle = 180.toDegree()
    private var onFinish: suspend () -> Unit = {}

    fun onFinish(block: suspend () -> Unit) {
        onFinish = block
    }

    companion object {
        fun commander(
            robotOnOdometry: ReceiveChannel<Stamped<Odometry>>,
            commandOut: SendChannel<NonOmnidirectional>,
            exceptions: SendChannel<ExceptionMessage>,
            block: CommanderBuilderDsl. () -> Unit = {}
        ) =
            CommanderBuilderDsl()
                .apply(block)
                .run {
                    Commander(
                        robotOnOdometry = robotOnOdometry,
                        commandOut = commandOut,
                        exceptions = exceptions,
                        directionLimit = directionLimit,
                        onFinish = onFinish)
                }
    }
}
