package cn.autolabor.localplanner

import org.mechdancer.BuilderDslMarker
import org.mechdancer.common.shape.Ellipse
import org.mechdancer.common.shape.Shape

@BuilderDslMarker
class PotentialFieldLocalPlannerBuilderDsl
private constructor() {
    var repelRange: Shape = Ellipse(.4, .5)
    var repelWeight: Double = .025
    var stepLength: Double = .05

    var lookAhead: Int = 8
    var minRepelPointsCount: Int = 16

    companion object {
        fun potentialFieldLocalPlanner(
            block: PotentialFieldLocalPlannerBuilderDsl.() -> Unit = {}
        ) =
            PotentialFieldLocalPlannerBuilderDsl()
                .apply(block)
                .apply {
                    require((repelWeight > 0))
                    require(stepLength > 0)
                    require(lookAhead > 0)
                    require(minRepelPointsCount > 0)
                }
                .run {
                    PotentialFieldLocalPlanner(
                        repelArea = repelRange,
                        repelWeight = repelWeight,
                        stepLength = stepLength,
                        lookAhead = lookAhead,
                        minRepelPointsCount = minRepelPointsCount)
                }
    }
}
