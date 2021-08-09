package cn.autolabor.amcl

import org.mechdancer.algebra.implement.vector.vector2DOfZero
import org.mechdancer.annotations.BuilderDslMarker
import kotlin.math.PI

/**
 * 粒子滤波器构建器
 */
@BuilderDslMarker
class AMCLFilterBuilderDsl private constructor() {
    var initWaitNumber = 10
    var minCount = 500
    var maxCount = 2000
    var tagPosition = vector2DOfZero()
    var dThresh = 0.05
    var aThresh = 10 * PI / 180
    var alpha1 = 0.2
    var alpha2 = 0.2
    var alpha3 = 0.2
    var alpha4 = 0.2
    var weightSigma = 1.0

    companion object {
        /** 构造粒子滤波器 */
        fun AMCLFilterBuild(block: AMCLFilterBuilderDsl.() -> Unit) =
            AMCLFilterBuilderDsl()
                .apply(block)
                .apply {
                    require(initWaitNumber >= 1)
                    require(minCount > 1)
                    require(maxCount > minCount)
                    require(dThresh > 0)
                    require(aThresh > 0)
                    require(0.0.rangeTo(1.0).contains(alpha1))
                    require(0.0.rangeTo(1.0).contains(alpha2))
                    require(0.0.rangeTo(1.0).contains(alpha3))
                    require(0.0.rangeTo(1.0).contains(alpha4))
                    require(weightSigma > 0)
                }
                .run {
                    AMCLFilter(
                        initWaitNumber = initWaitNumber,
                        minCount = minCount,
                        maxCount = maxCount,
                        tagPosition = tagPosition,
                        dThresh = dThresh,
                        aThresh = aThresh,
                        alpha1 = alpha1,
                        alpha2 = alpha2,
                        alpha3 = alpha3,
                        alpha4 = alpha4,
                        weightSigma = weightSigma
                    )
                }
    }
}
