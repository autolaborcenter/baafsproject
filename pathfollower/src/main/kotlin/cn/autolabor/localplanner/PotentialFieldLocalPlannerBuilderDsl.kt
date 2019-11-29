package cn.autolabor.localplanner

import org.mechdancer.algebra.function.vector.div
import org.mechdancer.algebra.function.vector.unaryMinus
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.annotations.BuilderDslMarker
import kotlin.math.pow

@BuilderDslMarker
class PotentialFieldLocalPlannerBuilderDsl
private constructor() {
    var repelWeight: Double = .025
    var stepLength: Double = .05

    var lookAhead: Int = 8
    var minRepelPointsCount: Int = 16

    private var repelField: (Vector2D) -> Vector2D = {
        -it / it.length.pow(3)
    }

    fun repel(block: (Vector2D) -> Vector2D) {
        repelField = block
    }

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
                            repelField = repelField,
                            repelWeight = repelWeight,
                            stepLength = stepLength,
                            lookAhead = lookAhead,
                            minRepelPointsCount = minRepelPointsCount)
                }
    }
}
