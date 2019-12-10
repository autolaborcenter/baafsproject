package org.mechdancer.global

import org.mechdancer.annotations.BuilderDslMarker
import org.mechdancer.common.Odometry
import org.mechdancer.geometry.angle.Angle
import org.mechdancer.geometry.angle.toDegree
import org.mechdancer.geometry.angle.toVector
import org.mechdancer.remote.presets.RemoteHub

@BuilderDslMarker
class GlobalPlannerBuilderDsl
private constructor() {
    var minTip: Angle = 90.toDegree()
    var searchCount: Int = 5
    private var localFirst: (Odometry) -> Boolean = {
        it.p.length < 2.0 && it.d.toVector().x > 0
    }

    fun localFirst(block: (Odometry) -> Boolean) {
        localFirst = block
    }

    private var painter: RemoteHub? = null

    companion object {
        fun pathPlanner(
            path: List<Odometry>,
            block: GlobalPlannerBuilderDsl.() -> Unit
        ) =
            GlobalPlannerBuilderDsl()
                .apply(block)
                .apply {
                    require(minTip.value > 0)
                    require(searchCount > 0)
                    require(localFirst(Odometry.pose()))
                }
                .run {
                    GlobalPathPlanner(path, minTip, searchCount, localFirst)
                        .also { it.painter = painter }
                }
    }
}
