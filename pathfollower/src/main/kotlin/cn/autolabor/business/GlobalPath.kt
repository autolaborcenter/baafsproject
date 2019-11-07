package cn.autolabor.business

import org.mechdancer.algebra.function.vector.euclid
import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.common.Odometry
import org.mechdancer.common.toTransformation
import org.mechdancer.common.transform
import java.util.concurrent.atomic.AtomicInteger

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
        require(isNotEmpty())
    }

    /** 查询/修改进度 */
    var progress: Double
        get() = (index.get() + 1.0) / size
        set(value) {
            require(value in 0.0..1.0) { "progress should be in [0, 1]" }
            index.set((value * size).toInt())
            searchAll = true
        }

    /** 推进进度 */
    operator fun plusAssign(i: Int) {
        index.updateAndGet { old -> kotlin.math.min(old + i, lastIndex) }
    }

    /** 根据 [robotOnMap] 查询局部路径并更新进度 */
    operator fun get(robotOnMap: Odometry): Sequence<Odometry> {
        val (p, _) = robotOnMap
        val mapToRobot = robotOnMap.toTransformation().inverse()
        // 推进进度
        val i = index.updateAndGet { old ->
            val end = when {
                searchAll || old == 0 -> size
                else                  -> kotlin.math.min(old + searchCount, size)
            }
            searchAll = false
            (old until end)
                .asSequence()
                .firstOrNull { localFirst(mapToRobot.transform(get(it))) }
            ?: old
        }
        // 产生全局路径（机器人坐标系下）
        return when {
            get(i).p euclid p < localRadius -> {
                asSequence()
                    .drop(i)
                    .map(mapToRobot::transform)
                    .takeWhile { it.p.norm() < searchCount }
            }
            else                            ->
                emptySequence()
        }
    }
}
