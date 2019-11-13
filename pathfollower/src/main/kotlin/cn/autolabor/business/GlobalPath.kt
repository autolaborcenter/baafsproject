package cn.autolabor.business

import org.mechdancer.algebra.function.vector.dot
import org.mechdancer.common.Odometry
import org.mechdancer.common.toTransformation
import org.mechdancer.common.transform
import org.mechdancer.geometry.angle.toVector
import org.mechdancer.paintPoses
import org.mechdancer.remote.presets.RemoteHub
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.min

/**
 * 全局路径
 * 线程安全
 */
class GlobalPath(
    core: List<Odometry>,
    private val searchCount: Int,
    private val localFirst: (Odometry) -> Boolean,

    private val painter: RemoteHub?
) : List<Odometry> by core {
    // 当前位置
    private var index = 0
    // 进度推进锁
    private val lock = ReentrantReadWriteLock()
    // 尖点序号缓存（区段末尾序号）
    private val tipsIndex: List<Int>
    // 允许搜索全部路径
    private var searchAll = true

    init {
        var dn =
            core.firstOrNull()?.d?.toVector()
            ?: throw IllegalArgumentException("global path cannot be empty")
        tipsIndex =
            core.asSequence()
                .drop(1)
                .mapIndexedNotNull { i, (_, d) ->
                    val `dn-1` = dn
                    dn = d.toVector()
                    i.takeIf { `dn-1` dot dn < 0 }
                }
                .toList() + lastIndex
    }

    /** 查询/修改进度 */
    var progress: Double
        get() = lock.read { index } + 1.0 / size
        set(value) {
            require(value in 0.0..1.0) { "progress should be in [0, 1]" }
            lock.write {
                index = (value * size).toInt()
                searchAll = true
            }
        }

    /** 根据 [robotOnMap] 查询局部路径并更新进度 */
    operator fun get(robotOnMap: Odometry): Sequence<Odometry> {
        val mapToRobot = robotOnMap.toTransformation().inverse()
        return lock.write {
            // 若路径已全部完成
            if (index == lastIndex)
                return@write mapToRobot
                                 .transform(last())
                                 .takeIf(localFirst)
                                 ?.let { sequenceOf(last()) }
                             ?: emptySequence()
            // 之前的进度
            val last = index
            // 当前区间末尾在尖点表里的序号
            val tipIndexIndex = tipsIndex.indices.first { tipsIndex[it] >= last }
            // 确定是否向下一区间转移
            val pass = last == tipsIndex[tipIndexIndex] && !localFirst(mapToRobot.transform(get(last)))
            // 区间尖点序号
            val tipIndex = tipsIndex[if (pass) tipIndexIndex + 1 else tipIndexIndex]
            // 搜索范围
            val area =
                (if (pass) last + 1 else last)..when {
                    searchAll -> lastIndex
                    else      -> min(tipIndex, last + searchCount)
                }
            // 在候选范围查找起始点
            area.asSequence()
                .map { i -> i to mapToRobot.transform(get(i)) }
                .firstOrNull { (_, pose) -> localFirst(pose) }
                ?.first
                ?.let {
                    // 推进进度
                    index = it
                    searchAll = false
                    subList(it, tipIndex + 1)
                        .also { list ->
                            painter?.run {
                                paintPoses("R 全局路径", list.take(50).map(mapToRobot::transform))
                            }
                        }
                        .asSequence()
                }
            ?: if (searchAll) emptySequence()
            else subList(area.first, area.last + 1).asSequence()
        }.map { mapToRobot.transform(it) }
    }
}
