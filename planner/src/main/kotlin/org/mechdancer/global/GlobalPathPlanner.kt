package org.mechdancer.global

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.mechdancer.algebra.function.vector.dot
import org.mechdancer.core.GlobalPlanner
import org.mechdancer.core.LocalPath
import org.mechdancer.geometry.angle.Angle
import org.mechdancer.geometry.angle.toVector
import org.mechdancer.geometry.transformation.Pose2D
import org.mechdancer.paintPoses
import org.mechdancer.remote.presets.RemoteHub
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.cos
import kotlin.math.min

/** 已知路径全局规划器 */
class GlobalPathPlanner
internal constructor(
    path: List<Pose2D>,
    minTip: Angle,
    private val searchCount: Int,
    private val localFirst: (Pose2D) -> Boolean
) : GlobalPlanner, List<Pose2D> by path {
    // 当前位置
    private var index = 0
    // 允许搜索全部路径
    private var searchAll = true
    // 进度推进锁
    private val lock = ReentrantReadWriteLock()
    // 尖点序号缓存（区段末尾序号）
    private val tipsIndex by lazy {
        // 尖点判断条件
        val cosMinTip = cos(minTip.rad)
        // 缓存尖点序号
        var dn = path.first().d.toVector()
        path.asSequence()
            .drop(1)
            .mapIndexedNotNull { i, (_, d) ->
                val `dn-1` = dn
                dn = d.toVector()
                i.takeIf { `dn-1` dot dn < cosMinTip }
            }
            .toList() + path.lastIndex
    }

    init {
        require(path.isNotEmpty()) { "global path cannot be empty" }
        GlobalScope.launch { tipsIndex }
    }

    /** 最近路径目标 */
    val firstTarget get() = get(index)

    /** 查询/修改进度 */
    var progress: Double
        get() = (lock.read { index } + 1).toDouble() / size
        set(value) {
            require(value in 0.0..1.0) { "progress should be in [0, 1]" }
            lock.write {
                index = (value * size).toInt()
                searchAll = true
            }
        }

    /** 设定路径循环 */
    @Volatile
    var isLoopOn = false

    /** 画图 */
    var painter: RemoteHub? = null

    override suspend fun plan(pose: Pose2D): LocalPath {
        fun onRobot(p: Pose2D) = pose.inverse() * p
        return lock.write {
            // 若路径已全部完成
            if (index == lastIndex)
                if (isLoopOn) index = 0
                else return@write onRobot(last())
                                      .takeIf(localFirst)
                                      ?.let(LocalPath::KeyPose)
                                  ?: LocalPath.Finish
            // 之前的进度
            val last = index
            // 当前区间末尾在尖点表里的序号
            val tipIndexIndex = tipsIndex.indices.first { tipsIndex[it] >= last }
            // 确定是否向下一区间转移
            val nextArea = if (last == tipsIndex[tipIndexIndex] && !get(last).let(::onRobot).let(localFirst)) 1 else 0
            // 搜索范围
            val area =
                (last + nextArea)..when {
                    searchAll -> lastIndex
                    else      -> min(tipsIndex[tipIndexIndex + nextArea], last + searchCount)
                }
            // 在候选范围查找起始点
            area.asSequence()
                .map { i -> i to get(i).let(::onRobot) }
                .firstOrNull { (_, pose) -> localFirst(pose) }
                ?.first
                ?.let { begin ->
                    // 推进进度
                    index = begin
                    searchAll = false
                    subList(begin, tipsIndex.first { it >= begin } + 1)
                }
                ?.map(::onRobot)
                ?.also { painter?.paintPoses("R 全局路径", it.take(200)) }
                ?.let { LocalPath.Path(it.asSequence()) }
            ?: if (searchAll) LocalPath.Failure
            else subList(area.first, area.last + 1)
                .asSequence()
                .map(::onRobot)
                .let(LocalPath::Path)
        }
    }
}
