package cn.autolabor.business

import org.mechdancer.algebra.function.vector.euclid
import org.mechdancer.common.Odometry
import org.mechdancer.common.invoke
import org.mechdancer.common.toTransformation
import java.util.concurrent.atomic.AtomicInteger

/**
 * 全局路径
 * 线程安全
 */
class GlobalPath(
    core: List<Odometry>,
    private val localLimit: Double,
    private val searchLength: Int
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
        // 推进进度
        val i = index.updateAndGet { old ->
            val end = when {
                searchAll || old == 0 -> size
                else                  -> kotlin.math.min(old + searchLength, size)
            }
            searchAll = false
            var dn = get(old).p euclid p
            (old + 1 until end)
                .firstOrNull { i ->
                    val `dn-1` = dn
                    dn = get(i).p euclid p
                    `dn-1` < localLimit && `dn-1` < dn
                }
                ?.let { it - 1 }
                ?: if (dn < localLimit) end - 1 else old
        }
        // 产生全局路径（机器人坐标系下）
        return when {
            get(i).p euclid p < localLimit -> {
                val mapToRobot = -robotOnMap.toTransformation()
                asSequence().drop(i).map { mapToRobot(it) }
            }
            else                           ->
                emptySequence()
        }
    }
}
