package cn.autolabor.localplanner

import org.mechdancer.algebra.doubleEquals
import org.mechdancer.algebra.function.vector.*
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.algebra.implement.vector.vector2DOfZero
import org.mechdancer.common.Odometry
import org.mechdancer.common.shape.Shape
import org.mechdancer.geometry.angle.toAngle
import org.mechdancer.geometry.angle.toVector
import java.util.*
import kotlin.math.max

/** 势场法局部规划器 */
class PotentialFieldLocalPlanner
internal constructor(
    val repelArea: Shape,
    private val stepLength: Double,
    private val attractWeight: Double
) {
    /**
     * 修饰函数
     *
     * @param global 机器人坐标系上的全局路径
     * @param obstacles 障碍物
     */
    fun modify(
        global: Sequence<Odometry>,
        obstacles: Collection<Vector2D>
    ): Sequence<Odometry> =
        obstacles
            .filter { it in repelArea }
            .takeUnless(Collection<*>::isEmpty)
            ?.let { repelPoints -> global.iterator().modifyInternal(repelPoints) }
        ?: global

    private fun Iterator<Odometry>.modifyInternal(repelPoints: Collection<Vector2D>) =
        sequence {
            val attractPoints = consume()?.let { mutableListOf(it) } ?: return@sequence
            val list: Queue<Vector2D> = LinkedList()
            var pose = Odometry.pose()
            while (true) {
                val (p0, d0) = pose
                // 从全局生产
                while (attractPoints.size < 8)
                    attractPoints += consume() ?: break
                // 从缓冲消费
                var dn = attractPoints.first().p euclid p0
                while (true) {
                    val `dn-1` = dn
                    dn = attractPoints.getOrNull(1)?.p?.euclid(p0) ?: break
                    if (dn < `dn-1`) attractPoints.removeAt(0)
                    else break
                }
                // 计算受力
                val fa = attractPoints.foldIndexed(vector2DOfZero()) { i, sum, (p, _) ->
                    sum + (p - p0).to2D() * (attractPoints.size - i)
                } / attractPoints.size.let { n -> n * (n + 1) / 2 } * attractWeight
                val fr = repelPoints.fold(vector2DOfZero()) { sum, p ->
                    val v = (p0 - p)
                    val l = v.norm()
                    sum + v / (l * l * l)
                }.let { (x, y) -> vector2DOf(max(x, .0), y) / max(10, repelPoints.size) }
                val f = (fa + fr).normalize().to2D()
                pose =
                    if (doubleEquals(f.norm(), .0))
                        Odometry(p0 + vector2DOf(stepLength, 0), d0)
                    else
                        Odometry(p0 + f * stepLength,
                                 attractPoints.fold(vector2DOfZero()) { sum, it -> sum + it.d.toVector() }.toAngle())
                if (list.any { doubleEquals(it euclid pose.p, .0) }) break
                if (list.size >= 5) list.poll()
                list.offer(pose.p)
                yield(pose)
            }
        }.take(50)

    private companion object {
        fun <T : Any> Iterator<T>.consume() = takeIf(Iterator<*>::hasNext)?.next()
    }
}
