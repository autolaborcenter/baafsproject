package cn.autolabor.business

import cn.autolabor.pathfollower.PathFollowerBuilderDsl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.mechdancer.BuilderDslMarker
import org.mechdancer.SimpleLogger
import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.geometry.angle.Angle
import org.mechdancer.geometry.angle.toDegree
import org.mechdancer.remote.presets.RemoteHub

@BuilderDslMarker
class BusinessBuilderDsl private constructor() {
    private var followerConfig: PathFollowerBuilderDsl.() -> Unit = {}
    var directionLimit: Angle = 180.toDegree()

    var localRadius: Double = .5
    var pathInterval: Double = .05
    private var localFirst: (Odometry) -> Boolean = { it.p.norm() < localRadius }

    var logger: SimpleLogger? = SimpleLogger("Business")
    var painter: RemoteHub? = null

    fun localFirst(block: (Odometry) -> Boolean) {
        localFirst = block
    }

    fun follower(block: PathFollowerBuilderDsl.() -> Unit) {
        followerConfig = block
    }

    companion object {
        fun CoroutineScope.business(
            robotOnMap: ReceiveChannel<Stamped<Odometry>>,
            robotOnOdometry: ReceiveChannel<Stamped<Odometry>>,
            commandOut: SendChannel<NonOmnidirectional>,
            exceptions: SendChannel<ExceptionMessage>,
            block: BusinessBuilderDsl.() -> Unit
        ) = BusinessBuilderDsl()
            .apply(block)
            .apply {
                require(localRadius > 0)
                require(pathInterval > 0)
            }
            .run {
                Business(
                        scope = this@business,
                        robotOnMap = robotOnMap,
                        robotOnOdometry = robotOnOdometry,
                        commandOut = commandOut,
                        exceptions = exceptions,

                        followerConfig = followerConfig,
                        directionLimit = directionLimit,

                        localRadius = localRadius,
                        pathInterval = pathInterval,
                        localFirst = localFirst,

                        logger = logger,
                        painter = painter)
            }
    }
}
