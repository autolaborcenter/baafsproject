package cn.autolabor.transform

import cn.autolabor.transform.struct.shortedFrom
import org.mechdancer.common.collection.map2d.CompletePairMap2D
import org.mechdancer.common.collection.map2d.viewBy
import kotlin.math.abs

class TransformSystem<Key : Any> {
    private val graphic =
        CompletePairMap2D<Key, Key, HashMap<Long, Transformation>> { _, _ -> hashMapOf() }

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
        if (s == t) throw IllegalArgumentException("source == target")
        val now = time ?: System.currentTimeMillis()

        synchronized(graphic) {
            val nodes = graphic.keys0
            if (s !in nodes) {
                graphic.put0(s)
                graphic.put1(s)
            }
            if (t !in nodes) {
                graphic.put0(t)
                graphic.put1(t)
            }

            graphic[s, t]!!.let { it[now] = transformation }
            graphic[t, s]!!.let { it[now] = -transformation }
        }
    }

    /** 获取 [pair] 之间 [time] 时刻的变换关系 */
    operator fun get(
        pair: Pair<Key, Key>,
        time: Long? = null
    ): SearchResult<Key>? {
        val (s, t) = pair
        if (s == t) throw IllegalArgumentException("source == target")
        val now = time ?: System.currentTimeMillis()

        return graphic
            .shortedFrom(s) {
                it.takeIf(Map<*, *>::isNotEmpty)
                    ?.map { (stamp, _) -> abs(now - stamp) }
                    ?.min()
                    ?.toDouble()
                ?: Double.MAX_VALUE
            }[t]
            ?.takeIf { (cost, list) ->
                cost < Double.MAX_VALUE && list.size > 1
            }
            ?.let { (cost, list) ->
                var transformation =
                    graphic[list[0], list[1]]!!
                        .minBy { (stamp, _) -> abs(now - stamp) }!!
                        .value
                for (i in 1 until list.lastIndex)
                    transformation *=
                        graphic[list[i], list[i + 1]]!!
                            .minBy { (stamp, _) -> abs(now - stamp) }!!
                            .value
                SearchResult(cost, list, transformation)
            }
    }

    override fun toString(): String {
        val now = System.currentTimeMillis()
        return graphic.viewBy(Any::toString, Any::toString) { v ->
            v.keys.min()?.let { now - it }?.toString() ?: "∞"
        }
    }
}
