package cn.autolabor.locator

import org.mechdancer.algebra.function.vector.*
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.common.Odometry
import org.mechdancer.geometry.angle.toAngle
import org.mechdancer.geometry.angle.toVector
import kotlin.math.max
import kotlin.math.tanh

/** 位姿推断器 */
data class Visionary(
    private val markOnOdometry: Odometry,
    private val expectation: Odometry,
    private val reliability: Double
) {
    /** 从新的里程推断位姿 */
    fun infer(odometry: Odometry) =
        expectation plusDelta (odometry minusState markOnOdometry)

    /** 融合一对新的关系 */
    fun fusion(
        newMarkOnOdometry: Odometry,
        newExpectation: Odometry,
        c: Double
    ): Visionary {
        // 里程计增量
        val passBy = newMarkOnOdometry minusState markOnOdometry
        // 用旧的关系推断
        val (p0, d0) = expectation plusDelta passBy
        // 新的推断
        val (p1, d1) = newExpectation
        // 走的越远，旧关系可靠性越低
        val r0 = reliability / max(c, passBy.p.norm())
        // 与旧关系的推断越不像，新关系的可靠性越低
        val r1 = 1 / max(c, p0 euclid p1)
        // 融合
        val e = Odometry(
            p = average(p0 to r0, p1 to r1),
            d = average(d0.toVector() to r0, d1.toVector() to r1).toAngle())
        // 生成新的推断关系
        return Visionary(newMarkOnOdometry, e, tanh(reliability * 2 * r1 / (r0 + r1)))
    }

    private companion object {
        fun average(a: Pair<Vector2D, Double>,
                    b: Pair<Vector2D, Double>
        ): Vector2D {
            val (v0, r0) = a
            val (v1, r1) = b
            return (v0 * r0 + v1 * r1) / (r0 + r1)
        }
    }
}
