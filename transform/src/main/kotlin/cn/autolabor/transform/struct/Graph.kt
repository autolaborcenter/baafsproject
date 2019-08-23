package cn.autolabor.transform.struct

import org.mechdancer.common.collection.map2d.IMap2D
import java.util.*

/** 安全获取链表元素 */
fun <TNode : Any, TPath : Path<TNode>>
    Map<TNode, Iterable<TPath>>.getOrEmpty(node: TNode) =
    getOrDefault(node, emptySet())

/** 获取链表元素的字符串表示 */
fun <TNode : Any, TPath : Path<TNode>>
    Map<TNode, Iterable<TPath>>.view(node: TNode) =
    "$node: ${getOrEmpty(node)}"

/**
 * 从[root]开始（反）拓扑排序
 * @return 连通的、可顺序构造的节点列表
 */
fun <TNode : Any, TPath : Path<TNode>>
    Map<TNode, Iterable<TPath>>.sort(root: TNode) =
    mutableListOf(root)
        .also { sub ->
            // 剩余项
            val rest = keys.toMutableSet().apply { remove(root) }
            // 已接纳指针
            var ptr = 0
            // 若仍有未连通的
            while (rest.isNotEmpty())
            // 已连通的全部检查过，直接返回
            // 否则尝试从邻接表中获取
                get(sub.getOrNull(ptr++) ?: break)
                    // 找到所有连接到的节点
                    ?.map(Path<TNode>::destination)
                    ?.toMutableSet()
                    // 取其中非叶子但未连接的
                    ?.apply { retainAll(rest) }
                    // 从未连接的移除，添加到已连接的
                    ?.also {
                        rest.removeAll(it)
                        sub.addAll(it)
                    }
        }

/** 构造包含[root]的连通子图 */
fun <TNode : Any, TPath : Path<TNode>>
    Map<TNode, Iterable<TPath>>.subWith(root: TNode) =
    sort(root)
        .associateWith {
            val list = getOrEmpty(it)
            (list as? Set) ?: list.toSet()
        }

/**
 * 单源最短路径算法
 */
infix fun <TNode>
    IMap2D<TNode, TNode, Number>.shortedFrom(
    source: TNode
): Map<TNode, Pair<Double, List<TNode>>> {
    val vertex = keys0.toMutableSet().apply { addAll(keys1) }.toSet()

    val queue = LinkedList<TNode>()
    val mark = hashSetOf<TNode>()
    val d = vertex
        .associateWith {
            (if (source == it) .0 else Double.MAX_VALUE) to listOf(source)
        }
        .toMutableMap()

    var head = source
    while (true) {
        values0(head)
            .asSequence()
            .mapNotNull { (key, value) ->
                value
                    ?.toDouble()
                    ?.let { key to it }
            }
            .forEach { (t, c) ->
                val ds = d[head]!!
                val dt = d[t]!!

                val new = ds.first + c
                if (new < dt.first) {
                    d[t] = new to (ds.second + t)
                    if (t !in mark) {
                        mark.add(t)
                        queue.offer(t)
                    }
                }
            }
        // 取出头
        head = queue.poll() ?: break
        mark.remove(head)
    }

    return d
}
