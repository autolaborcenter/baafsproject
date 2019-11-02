package cn.autolabor.locator

import org.mechdancer.algebra.function.vector.euclid
import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.common.Odometry
import kotlin.math.min
import kotlin.math.tanh

/** 位姿推断器 */
data class Visionary(
    val markOnOdometry: Odometry,
    val expectation: Odometry,
    val reliability: Double
) {
    /** 从新的里程推断位姿 */
    fun infer(odometry: Odometry) =
        expectation plusDelta (odometry minusState markOnOdometry)

    /** 融合一对新的关系 */
    fun fusion(
        newMarkOnOdometry: Odometry,
        newExpectation: Odometry,
        odometryReliableRange: Double,
        filterReliableRange: Double
    ): Visionary {
        // 里程计增量
        val passBy = newMarkOnOdometry minusState markOnOdometry
        // 用旧的关系推断
        val conjecture = expectation plusDelta passBy
        // 走的越远，旧关系可靠性越低
        val r0 = reliability * (1 - min(1.0, passBy.p.norm() / odometryReliableRange))
        // 与旧关系的推断越不像，新关系的可靠性越低
        val r1 = (1 - r0 * (1 - min(1.0, (conjecture.p euclid newExpectation.p) / filterReliableRange)))
        // 生成新的推断关系
        return Visionary(
            newMarkOnOdometry,
            average(conjecture to r0, newExpectation to r1),
            tanh(2 * r1 / (r0 + r1)))
    }
}
