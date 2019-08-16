package cn.autolabor.utilities

import org.mechdancer.algebra.function.vector.minus
import org.mechdancer.algebra.function.vector.plus
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.geometry.angle.Angle
import org.mechdancer.geometry.angle.rotate
import org.mechdancer.geometry.angle.unaryMinus

/**
 * 里程计
 * @param s 总里程（米）
 * @param a 总转角（弧度）
 * @param p 位置
 * @param d 方向
 */
data class Odometry(
    val s: Double,
    val a: Double,
    val p: Vector2D,
    val d: Angle
) {
    /**
     * 里程增加
     */
    infix fun plusDelta(delta: Odometry) =
        Odometry(s + delta.s,
                 a + delta.a,
                 p + delta.p.rotate(d),
                 d rotate delta.d)

    /**
     * 里程减少
     */
    infix fun minusDelta(delta: Odometry) =
        (d rotate -delta.d).let {
            Odometry(s - delta.s,
                     a - delta.a,
                     p - delta.p.rotate(-it),
                     it)
        }

    /**
     * 里程减少
     */
    infix fun minusState(mark: Odometry) =
        Odometry(s - mark.s,
                 a - mark.a,
                 (p - mark.p).rotate(-mark.d),
                 d.rotate(-mark.d))
}
