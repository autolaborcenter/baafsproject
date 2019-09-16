package cn.autolabor.locator

import cn.autolabor.locator.ParticleFilter.StepState
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOfZero
import kotlin.math.PI

/**
 * 粒子滤波器构建器
 */
class ParticleFilterBuilder {
    var size: Int = 128

    var locatorOnRobot: Vector2D = vector2DOfZero()
    var locatorWeight: Double? = null
        get() = field ?: 0.5 * size

    var maxInterval: Long = 500L
    var maxInconsistency: Double = 0.2
    var maxAge: Int = 10

    var sigmaRange: ClosedFloatingPointRange<Double> = (0.1 * PI)..(0.25 * PI)

    private var stepFeedback: ((StepState) -> Unit)? = null
    fun feedback(block: (StepState) -> Unit) {
        stepFeedback = block
    }

    companion object {
        /** 粒子滤波器 DSL */
        fun particleFilter(block: ParticleFilterBuilder.() -> Unit) =
            ParticleFilterBuilder()
                .apply(block)
                .apply {
                    require(size > 1)
                    require(locatorWeight!! > 0)
                    require(maxInterval > 0)
                    require(maxInconsistency > 0)
                    require(maxAge > 0)
                    require(sigmaRange.start > 0)
                    require(sigmaRange.endInclusive > sigmaRange.start)
                }
                .run {
                    ParticleFilter(size = size,
                                   locatorOnRobot = locatorOnRobot,
                                   locatorWeight = locatorWeight!!,
                                   maxInterval = maxInterval,
                                   maxInconsistency = maxInconsistency,
                                   maxAge = maxAge,
                                   stepFeedback = stepFeedback,
                                   sigmaRange = sigmaRange)
                }
    }
}
