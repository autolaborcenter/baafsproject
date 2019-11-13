package cn.autolabor.localplanner

import org.mechdancer.BuilderDslMarker
import org.mechdancer.common.shape.Ellipse
import org.mechdancer.common.shape.Shape

@BuilderDslMarker
class PotentialFieldLocalPlannerBuilderDsl
private constructor() {
    var attractRange: Shape = Ellipse(.3, .8)
    var repelRange: Shape = Ellipse(.4, .5)
    var stepLength: Double = .05
    var attractWeight: Double = 5.0

    companion object {
        fun potentialFieldLocalPlanner(
            block: PotentialFieldLocalPlannerBuilderDsl.() -> Unit = {}
        ) =
            PotentialFieldLocalPlannerBuilderDsl()
                .apply(block)
                .apply {
                    require(stepLength > 0)
                    require((attractWeight > 0))
                }
                .run {
                    PotentialFieldLocalPlanner(
                            attractArea = attractRange,
                            repelArea = repelRange,
                            stepLength = stepLength,
                            attractWeight = attractWeight)
                }
    }
}
