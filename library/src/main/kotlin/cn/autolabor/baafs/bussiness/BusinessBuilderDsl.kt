package cn.autolabor.baafs.bussiness

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.annotations.BuilderDslMarker
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.core.LocalPath

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
            globalOnRobot: SendChannel<LocalPath>,
            block: BusinessBuilderDsl.() -> Unit
        ) = BusinessBuilderDsl()
            .apply(block)
            .apply {
                require(localRadius > 0)
                require(pathInterval > 0)
            }
            .run {
                Business(scope = this@startBusiness,
                         robotOnMap = robotOnMap,
                         globalOnRobot = globalOnRobot,

                         pathInterval = pathInterval,
                         localFirst = localFirst)
            }
    }
}
