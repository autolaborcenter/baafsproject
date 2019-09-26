package cn.autolabor.locator

import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOfZero
import kotlin.math.PI

/**
 * 粒子滤波器构建器
 */
class ParticleFilterBuilder {
    var count: Int = 128

    var beaconOnRobot: Vector2D = vector2DOfZero()
    var beaconWeight: Double? = null
        get() = field ?: 0.5 * count

    var maxInterval: Long = 500L
    var maxInconsistency: Double = 0.2
    var maxAge: Int = 50

    var sigma: Double = 0.1 * PI

    companion object {
        /** 粒子滤波器 DSL */
        fun particleFilter(block: ParticleFilterBuilder.() -> Unit) =
            ParticleFilterBuilder()
                .apply(block)
                .apply {
                    require(count > 1)
                    require(beaconWeight!! >= 0)
                    require(maxInterval > 0)
                    require(maxInconsistency > 0)
                    require(maxAge > 0)
                    require(sigma > 0)
                }
                .run {
                    ParticleFilter(count = count,
                                   locatorOnRobot = beaconOnRobot,
                                   locatorWeight = beaconWeight!!,
                                   maxInterval = maxInterval,
                                   maxInconsistency = maxInconsistency,
                                   maxAge = maxAge,
                                   sigma = sigma)
                }
    }
}
