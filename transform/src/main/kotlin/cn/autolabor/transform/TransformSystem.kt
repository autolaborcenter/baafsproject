package cn.autolabor.transform

import cn.autolabor.transform.struct.shortedFrom
import org.mechdancer.common.collection.map2d.CompletePairMap2D
import org.mechdancer.common.collection.map2d.viewBy
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.abs

/** 以 [Key] 为键存储变换关系的变换系统 */
class TransformSystem<Key : Any> {
    // 变换关系存储在完全图中
    private val graphic =
        CompletePairMap2D<Key, Key, HashMap<Long, Transformation>>
        { _, _ -> hashMapOf() }
    // 在添加变换时锁住
    private val lock = ReentrantReadWriteLock()

    companion object {
        const val Constant = -1L

        // 更新
        private fun HashMap<Long, Transformation>.update(
            time: Long,
            new: Transformation
        ) {
            if (time < 0) clear()
            else keys
                .filter { it < 0 }
                .forEach { remove(it) }
            this[time] = new
        }
    }

    /**
     * 图的查询结果
     * @param cost 路径开销
     * @param path 途径的变换关系
     * @param transformation 总变换
     */
    data class SearchResult<Key>(
        val cost: Double,
        val path: List<Key>,
        val transformation: Transformation
    )

    /** 添加 [pair] 之间 [time] 时刻的变换关系 [transformation] */
    operator fun set(
        pair: Pair<Key, Key>,
        time: Long? = null,
        transformation: Transformation
    ) {
        val (s, t) = pair
        require(s != t) { "source == target" }

        val newTime = time ?: System.currentTimeMillis()
        lock.write {
            // 查询坐标系是否已知
            if (s !in graphic.keys0) {
                graphic.put0(s)
                graphic.put1(s)
            }
            if (t !in graphic.keys0) {
                graphic.put0(t)
                graphic.put1(t)
            }
            // 添加正反变换
            graphic[s, t]!!.update(newTime, transformation)
            graphic[t, s]!!.update(newTime, -transformation)
        }
    }

    /** 获取 [pair] 之间 [time] 时刻的变换关系 */
    operator fun get(
        pair: Pair<Key, Key>,
        time: Long? = null
    ): SearchResult<Key>? {
        val (s, t) = pair
        require(s != t) { "source == target" }

        val now = time ?: System.currentTimeMillis()
        return lock.read {
            graphic
                // 查找最短路径
                .shortedFrom(s) {
                    it.takeIf(Map<*, *>::isNotEmpty)
                        ?.map { (stamp, _) ->
                            if (stamp < 0) 0L
                            else abs(now - stamp)
                        }
                        ?.min()
                        ?.toDouble()
                    ?: Double.MAX_VALUE
                }[t]
                // ↑ 取出到目标坐标系的变换关系
                // ↓ 判断目标坐标系可达
                ?.takeIf { (cost, list) ->
                    cost < Double.MAX_VALUE && list.size > 1
                }
                // 生成总变换
                ?.let { (cost, list) ->
                    val get = { i: Int ->
                        graphic[list[i], list[i + 1]]!!
                            .minBy { (stamp, _) ->
                                if (stamp < 0) 0L
                                else abs(now - stamp)
                            }!!.value
                    }

                    (1 until list.lastIndex)
                        .fold(get(0)) { sum, i -> sum * get(i) }
                        .let { SearchResult(cost, list, it) }
                }
        }
    }

    override fun toString(): String {
        val now = System.currentTimeMillis()
        return lock.read {
            graphic.viewBy(Any::toString, Any::toString) { v ->
                // 显示表中最新变换关系的更新时间
                v.keys
                    .min()
                    ?.let { if (it < 0) 0L else now - it }
                    ?.toString()
                ?: "∞"
            }
        }
    }
}
