package cn.autolabor.locator

import org.mechdancer.algebra.function.vector.euclid
import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.average
import org.mechdancer.geometry.transformation.Pose2D
import org.mechdancer.geometry.transformation.minusState
import org.mechdancer.geometry.transformation.plusDelta
import kotlin.math.tanh

/** 位姿推断器 */
data class Visionary(
    val markOnOdometry: Pose2D,
    val expectation: Pose2D,
    val reliability: Double
) {
    /** 从新的里程推断位姿 */
    fun infer(odometry: Pose2D) =
        expectation plusDelta (odometry minusState markOnOdometry)

    /** 融合一对新的关系 */
    fun fusion(
        newMarkOnOdometry: Pose2D,
        newExpectation: Pose2D,
        odometryReliableRange: Double,
        filterReliableRange: Double
    ): Visionary {
        val gauss0 = Gauss(.0, odometryReliableRange / 3)
        val gauss1 = Gauss(.0, filterReliableRange / 3)

        // 里程计增量
        val passBy = newMarkOnOdometry minusState markOnOdometry
        // 用旧的关系推断
        val conjecture = expectation plusDelta passBy
        // 走的越远，旧关系可靠性越低
        val r0 = reliability * gauss0.p(passBy.p.norm())
        // 与旧关系的推断越不像，新关系的可靠性越低
        val r1 = (1 - r0 * gauss1.p(conjecture.p euclid newExpectation.p))
        // 生成新的推断关系
        return Visionary(
            newMarkOnOdometry,
            average(conjecture to r0, newExpectation to r1),
            tanh(2 * r1 / (r0 + r1)))
    }
}
