package cn.autolabor.locator

import cn.autolabor.locator.ParticleFilter.StepState
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOfZero

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
                    require(maxInterval > 0)
                    require(maxAge > 0)
                }
                .run {
                    ParticleFilter(size = size,
                                   locator = locatorOnRobot,
                                   locatorWeight = locatorWeight!!,
                                   maxInterval = maxInterval,
                                   maxInconsistency = maxInconsistency,
                                   maxAge = maxAge,
                                   stepFeedback = stepFeedback)
                }
    }
}
