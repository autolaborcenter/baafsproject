package cn.autolabor.localplanner

import org.mechdancer.algebra.doubleEquals
import org.mechdancer.algebra.function.vector.*
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.algebra.implement.vector.vector2DOfZero
import org.mechdancer.common.Odometry
import org.mechdancer.common.toTransformation
import org.mechdancer.geometry.angle.toAngle
import org.mechdancer.geometry.angle.toVector
import java.util.*
import kotlin.math.max

/**
 * 势场法局部规划器
 *
 * @param repelField 斥力范围
 * @param repelWeight 斥力权重
 * @param stepLength 步长
 * @param lookAhead 前瞻点数
 * @param minRepelPointsCount 最小斥力归一化点数
 */
class PotentialFieldLocalPlanner
internal constructor(
    private val repelField: (Vector2D) -> Vector2D,
    private val repelWeight: Double,
    private val stepLength: Double,
    private val lookAhead: Int,
    private val minRepelPointsCount: Int
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
        sequence {
            val iter = global.iterator()
            val attractPoints = iter.consume()?.let { mutableListOf(it) } ?: return@sequence
            val list: Queue<Vector2D> = LinkedList()
            var pose = Odometry.pose()
            while (true) {
                val robotToPose = pose.toTransformation().inverse()
                val (p0, d0) = pose
                // 从全局生产
                while (attractPoints.size < lookAhead)
                    attractPoints += iter.consume() ?: break
                // 从缓冲消费
                var dn = attractPoints.first().p euclid p0
                while (true) {
                    val `dn-1` = dn
                    dn = attractPoints.getOrNull(1)?.p?.euclid(p0) ?: break
                    if (dn < `dn-1`) attractPoints.removeAt(0)
                    else break
                }
                // 计算受力
                var count = 0
                val fa =
                    attractPoints
                        .foldIndexed(vector2DOfZero()) { i, sum, (p, _) ->
                            val w = attractPoints.size - i
                            count += w
                            sum + (p - p0).to2D() * w
                        } / count
                count = 0
                val fr =
                    obstacles
                        .fold(vector2DOfZero()) { sum, p ->
                            val v = robotToPose(p).to2D()
                            val f = repelField(v)
                            if (f.length > 0) ++count
                            sum + f
                        }
                        .let { (x, y) ->
                            vector2DOf(max(x, .0), y) * repelWeight / max(minRepelPointsCount, count)
                        }
                val f = (fa + fr).normalize().to2D()
                // 步进
                pose =
                    if (doubleEquals(f.norm(), .0))
                        Odometry(p0 + vector2DOf(stepLength, 0), d0)
                    else
                        Odometry(p0 + f * stepLength,
                                 attractPoints.fold(vector2DOfZero()) { sum, it ->
                                     sum + it.d.toVector()
                                 }.toAngle())
                if (list.any { (it euclid pose.p) < .01 }) break
                if (list.size >= 5) list.poll()
                list.offer(pose.p)
                yield(pose)
            }
        }.take(50)

    private companion object {
        fun <T : Any> Iterator<T>.consume() = takeIf(Iterator<*>::hasNext)?.next()
    }
}
