package cn.autolabor.transform.struct

import org.mechdancer.common.collection.map2d.IMap2D
import java.util.*

/**
 * 逆转 [to]
 */
infix fun <A, B> A.from(that: B): Pair<B, A> = Pair(that, this)

/**
 * 单源最短路径算法
 * 通过将无向图表现双向图兼容有向图和无向图
 */
fun <T, V>
    IMap2D<T, T, V>.shortedFrom(
    source: T,
    cost: (V) -> Double
): Map<T, Pair<Double, List<T>>> {
    // 取出顶点
    val vertex = keys0.toMutableSet().apply { addAll(keys1) }.toSet()
    // 初始化算法使用的容器
    val queue = LinkedList<T>()
    val mark = hashSetOf<T>()
    val d = vertex
        .associateWith {
            (if (source == it) .0 else Double.MAX_VALUE) to listOf(source)
        }
        .toMutableMap()
    // 从起点开始扩展
    var head = source
    while (true) {
        // 对所有从某个点出发的路径
        values0(head)
            .asSequence()
            .mapNotNull { (key, value) ->
                value?.let { key to cost(value) }
            }
            .forEach { (target, c) ->
                val ds = d[head]!!
                val dt = d[target]!!

                val new = ds.first + c
                if (new < dt.first) {
                    d[target] = new to (ds.second + target)
                    if (mark.add(target)) queue.offer(target)
                }
            }
        // 取出头，队空结束
        head = queue.poll() ?: break
        mark.remove(head)
    }

    return d
}
