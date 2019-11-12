package cn.autolabor.business

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.mechdancer.BuilderDslMarker
import org.mechdancer.SimpleLogger
import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.remote.presets.RemoteHub

@BuilderDslMarker
class BusinessBuilderDsl private constructor() {
    var localRadius: Double = .5
    var pathInterval: Double = .05

    private var localFirst: (Odometry) -> Boolean = { it.p.norm() < localRadius }

    fun localFirst(block: (Odometry) -> Boolean) {
        localFirst = block
    }

    companion object {
        fun CoroutineScope.startBusiness(
            robotOnMap: ReceiveChannel<Stamped<Odometry>>,
            globalOnRobot: SendChannel<Pair<Sequence<Odometry>, Double>>,
            block: BusinessBuilderDsl.() -> Unit
        ) = BusinessBuilderDsl()
            .apply(block)
            .apply {
                require(localRadius > 0)
                require(pathInterval > 0)
            }
            .run {
                Business(
                    scope = this@startBusiness,
                    robotOnMap = robotOnMap,
                    globalOnRobot = globalOnRobot,

                    localRadius = localRadius,
                    pathInterval = pathInterval,
                    localFirst = localFirst)
            }
    }
}
