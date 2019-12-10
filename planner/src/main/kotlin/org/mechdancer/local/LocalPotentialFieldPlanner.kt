package org.mechdancer.local

import org.mechdancer.algebra.core.Vector
import org.mechdancer.algebra.function.vector.*
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.algebra.implement.vector.vector2DOfZero
import org.mechdancer.common.Odometry
import org.mechdancer.common.toTransformation
import org.mechdancer.core.LocalPath
import org.mechdancer.core.LocalPlanner
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
class LocalPotentialFieldPlanner
internal constructor(
    private val repelField: (Vector2D) -> Vector2D,
    private val repelWeight: Double,
    private val stepLength: Double,
    private val lookAhead: Int,
    private val minRepelPointsCount: Int,
    private val obstacleSource: suspend () -> Collection<Vector2D>
) : LocalPlanner {
    override suspend fun plan(path: LocalPath): LocalPath {
        val obstacles = obstacleSource()
        return when (path) {
            LocalPath.Finish,
            LocalPath.Failure,
            is LocalPath.KeyPose -> path
            is LocalPath.Path    ->
                sequence {
                    val iter = path.path.iterator()
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
                        // 计算引力
                        val fa =
                            attractPoints
                                .asSequence()
                                .map(Odometry::p)
                                .map(Vector::normalize)
                                .map(Vector::to2D)
                                .toList()
                                .sumByVector2D() / attractPoints.size
                        // 计算斥力
                        val weight: Double
                        val fr =
                            obstacles
                                .asSequence()
                                .map(robotToPose::invoke)
                                .map(Vector::to2D)
                                .map(repelField)
                                .toList()
                                .also { weight = repelWeight / max(minRepelPointsCount, it.size) }
                                .sumByVector2D()
                                .let { (x, y) -> vector2DOf(max(x, .0), y) }
                                .let(poseToRobot::invoke)
                                .to2D() * weight
                        // 计算合力（方向），落入局部势垒则直接前进
                        val f = (fa + fr).takeIf { it.length > 1E-6 }?.normalize()?.to2D() ?: vector2DOf(1, 0)
                        // 步进
                        pose = Odometry(p = p0 + f * stepLength,
                                        d = attractPoints.sumByVector2D { it.d.toVector() }.toAngle())
                        if (list.any { (it euclid pose.p) < .01 }) break
                        if (list.size >= lookAhead) list.poll()
                        list.offer(pose.p)
                        yield(pose)
                    }
                }.let { LocalPath.Path(it.take(50)) }
        }

    }

    private companion object {
        fun <T : Any> Iterator<T>.consume() = takeIf(Iterator<*>::hasNext)?.next()

        fun Iterable<Vector2D>.sumByVector2D() =
            fold(vector2DOfZero()) { sum, it -> sum + it }

        inline fun <T> Iterable<T>.sumByVector2D(block: (T) -> Vector2D) =
            fold(vector2DOfZero()) { sum, it -> sum + block(it) }
    }
}
