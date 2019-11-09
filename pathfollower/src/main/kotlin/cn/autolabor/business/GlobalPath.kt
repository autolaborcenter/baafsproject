package cn.autolabor.business

import org.mechdancer.algebra.function.vector.dot
import org.mechdancer.algebra.function.vector.euclid
import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.common.Odometry
import org.mechdancer.common.toTransformation
import org.mechdancer.common.transform
import org.mechdancer.geometry.angle.toVector
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

/**
 * 全局路径
 * 线程安全
 */
class GlobalPath(
    core: List<Odometry>,
    private val localRadius: Double,
    private val searchCount: Int,
    private val localFirst: (Odometry) -> Boolean
) : List<Odometry> by core {
    // 当前位置
    private val index = AtomicInteger(0)
    // 允许搜索全部路径
    private var searchAll = false

    init {
        require(core.isNotEmpty())
    }

    /** 查询/修改进度 */
    var progress: Double
        get() = (index.get() + 1.0) / size
        set(value) {
            require(value in 0.0..1.0) { "progress should be in [0, 1]" }
            index.set((value * size).toInt())
            searchAll = true
        }

    /** 根据 [robotOnMap] 查询局部路径并更新进度 */
    operator fun get(robotOnMap: Odometry): Sequence<Odometry> {
        val mapToRobot = robotOnMap.toTransformation().inverse()
        // 推进进度
        val i = index.updateAndGet { old ->
            val end = when {
                searchAll || old == 0 -> size
                else                  -> min(old + searchCount, size)
            }
            searchAll = false
            val candidates =
                (old until end).map { i -> i to mapToRobot.transform(get(i)) }
            candidates
                .firstOrNull { (_, pose) -> localFirst(pose) }
                ?.first
            ?: candidates
                .asSequence()
                .drop(1)
                .firstOrNull { (_, pose) ->
                    pose.p.norm() < localRadius
                }
                ?.first
            ?: old
        }
        // 产生全局路径（机器人坐标系下）
        val (p0, d0) = get(i)
        var dn = d0.toVector()
        val (p, _) = robotOnMap
        return when {
            p0 euclid p < localRadius -> {
                slice(i until min(i + searchCount, size))
                    .asSequence()
                    .takeWhile { (_, d) ->
                        val `dn-1` = dn
                        dn = d.toVector()
                        dn dot `dn-1` >= 0
                    }
                    .map(mapToRobot::transform)
            }
            else                      ->
                emptySequence()
        }
    }
}
