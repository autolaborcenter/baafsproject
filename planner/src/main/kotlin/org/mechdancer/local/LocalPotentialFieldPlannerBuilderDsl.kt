package org.mechdancer.local

import org.mechdancer.algebra.function.vector.div
import org.mechdancer.algebra.function.vector.unaryMinus
import org.mechdancer.algebra.implement.vector.Vector2D
import kotlin.math.pow

class LocalPotentialFieldPlannerBuilderDsl
private constructor() {
    var repelWeight: Double = .025
    var stepLength: Double = .05

    var lookAhead: Int = 8
    var minRepelPointsCount: Int = 16

    private var repelField: (Vector2D) -> Vector2D =
        { -it / it.length.pow(3) }

    private var obstacleSource: suspend () -> Collection<Vector2D> =
        { emptyList() }

    fun repel(block: (Vector2D) -> Vector2D) {
        repelField = block
    }

    fun obstacles(block: suspend () -> Collection<Vector2D>) {
        obstacleSource = block
    }

    companion object {
        fun potentialFieldPlanner(
            block: LocalPotentialFieldPlannerBuilderDsl.() -> Unit = {}
        ) =
            LocalPotentialFieldPlannerBuilderDsl()
                .apply(block)
                .apply {
                    require((repelWeight > 0))
                    require(stepLength > 0)
                    require(lookAhead > 0)
                    require(minRepelPointsCount > 0)
                }
                .run {
                    LocalPotentialFieldPlanner(
                        repelField = repelField,
                        repelWeight = repelWeight,
                        stepLength = stepLength,
                        lookAhead = lookAhead,
                        minRepelPointsCount = minRepelPointsCount,
                        obstacleSource = obstacleSource
                    )
                }
    }
}
