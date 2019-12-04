package cn.autolabor.locator

import org.mechdancer.Schmitt
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOfZero
import org.mechdancer.annotations.BuilderDslMarker
import kotlin.math.PI

/**
 * 粒子滤波器构建器
 */
@BuilderDslMarker
class ParticleFilterBuilderDsl private constructor() {
    // 粒子数
    var count: Int = 128
    // 信标位置
    var beaconOnRobot: Vector2D = vector2DOfZero()
    // 信标最大权重
    var beaconWeight: Double? = null
        get() = field ?: .5 * count
    // 夹逼配对最大间隔
    var maxInterval: Long = 500L
    // 融合双方最大不一致性
    var maxInconsistency: Double = .10
    // 例子最大寿命
    var maxAge: Int = 50
    // 重采样方向标准差
    var sigma: Double = .10 * PI
    // 收敛判定
    private var convergence: (FusionQuality) -> Boolean = { true }
    private var divergence: (FusionQuality) -> Boolean = { (age, _, _) -> age < .05 }

    // 收敛条件
    fun convergence(block: (FusionQuality) -> Boolean) {
        convergence = block
    }

    // 发散条件
    fun divergence(block: (FusionQuality) -> Boolean) {
        divergence = block
    }

    companion object {
        /** 构造粒子滤波器 */
        fun particleFilter(block: ParticleFilterBuilderDsl.() -> Unit) =
            ParticleFilterBuilderDsl()
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
                                   sigma = sigma,
                                   predicate = Schmitt(
                                       positive = convergence,
                                       negative = divergence))
                }
    }
}
