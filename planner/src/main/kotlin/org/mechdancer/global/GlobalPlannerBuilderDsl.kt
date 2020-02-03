package org.mechdancer.global

import org.mechdancer.annotations.BuilderDslMarker
import org.mechdancer.geometry.angle.Angle
import org.mechdancer.geometry.angle.toDegree
import org.mechdancer.geometry.angle.toVector
import org.mechdancer.geometry.transformation.Pose2D
import org.mechdancer.geometry.transformation.pose2D
import org.mechdancer.remote.presets.RemoteHub
import kotlin.math.PI

@BuilderDslMarker
class GlobalPlannerBuilderDsl
private constructor() {
    var minTip: Angle = 90.toDegree()
    var searchCount: Int = 5
    private var localFirst: (Pose2D) -> Boolean = {
        it.p.length < 2.0 && it.d.toVector().x > 0
    }

    fun localFirst(block: (Pose2D) -> Boolean) {
        localFirst = block
    }

    private var painter: RemoteHub? = null

    companion object {
        fun pathPlanner(
            path: List<Pose2D>,
            block: GlobalPlannerBuilderDsl.() -> Unit
        ) =
            GlobalPlannerBuilderDsl()
                .apply(block)
                .apply {
                    require(minTip.rad in .0..PI)
                    require(searchCount > 0)
                    require(localFirst(pose2D()))
                }
                .run {
                    GlobalPathPlanner(path, minTip, searchCount, localFirst)
                        .also { it.painter = painter }
                }
    }
}
