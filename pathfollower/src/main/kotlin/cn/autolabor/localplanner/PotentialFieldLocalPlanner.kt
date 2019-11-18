package cn.autolabor.localplanner

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
 * @param repelField 斥力场函数
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
                val poseToRobot = pose.toTransformation()
                val robotToPose = poseToRobot.inverse()
                val (p0, _) = pose
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
                val fa =
                    attractPoints
                        .sumByVector2D { (p, _) ->
                            (p - p0).normalize().to2D()
                        } / attractPoints.size
                var count = 0
                val fr =
                    obstacles
                        .sumByVector2D { repelField(robotToPose(it).to2D()).apply { if (length > 0) ++count } }
                        .let { (x, y) -> poseToRobot(vector2DOf(max(x, .0), y)) }
                        .to2D() * repelWeight / max(minRepelPointsCount, count)
                val f = (fa + fr).normalize().to2D()
                val tp = p0 + (f.takeIf { it.norm() > 1E-6 } ?: vector2DOf(1, 0)) * stepLength
                val td = attractPoints.sumByVector2D { it.d.toVector() }.toAngle()
                // 步进
                pose = Odometry(tp, td)
                if (list.any { (it euclid pose.p) < .01 }) break
                if (list.size >= lookAhead) list.poll()
                list.offer(pose.p)
                yield(pose)
            }
        }.take(50)

    private companion object {
        fun <T : Any> Iterator<T>.consume() = takeIf(Iterator<*>::hasNext)?.next()

        inline fun <T> Iterable<T>.sumByVector2D(block: (T) -> Vector2D) =
            fold(vector2DOfZero()) { sum, it -> sum + block(it) }
    }
}
