package cn.autolabor.amcl

import org.mechdancer.algebra.function.vector.div
import org.mechdancer.algebra.function.vector.plus
import org.mechdancer.algebra.implement.vector.Vector3D
import org.mechdancer.algebra.implement.vector.vector3DOf
import kotlin.math.PI
import kotlin.math.abs

data class Index3D(val x: Int, val y: Int, val z: Int) {
    constructor(vector: Vector3D)
            : this(vector.x.toInt(), vector.y.toInt(), vector.z.toInt())

    operator fun get(i: Int) =
        when (i) {
            0    -> x
            1    -> y
            2    -> z
            else -> throw IllegalArgumentException()
        }

    infix fun mostDifferentIndexWith(others: Index3D) =
        sequenceOf(x - others.x, y - others.y, z - others.z)
            .map(::abs)
            .withIndex()
            .maxBy { (_, value) -> value }!!
            .index

    fun toVector() =
        vector3DOf(x, y, z)
}

class KDTreeNode(
    var isLeaf: Boolean,
    var depth: Int,
    var key: Index3D,
    var value: Double,
    var pivotDim: Int = -1,
    var pivotValue: Double = 0.0,
    var cluster: Int = -1,
    var children: Pair<KDTreeNode, KDTreeNode>? = null)

/** k 维树 */
class KdTree {
    var size = Vector3D(.1, .1, 10 * PI / 180)
    var nodes = mutableListOf<KDTreeNode>()
    var leafCount = 0

    private var root: KDTreeNode? = null

    /** 插入节点 */
    fun insert(pose: Vector3D, value: Double) {
        root = insertNode(null, root, Index3D(pose / size), value)
    }

    /** 清空 */
    fun clear() {
        root = null
        leafCount = 0
        nodes.clear()
    }

    /** 计算聚类 */
    fun cluster() {
        var clusterCount = 0
        nodes
            .filter { it.isLeaf }
            .onEach { it.cluster = -1 }
            .asReversed()
            .asSequence()
            .filter { node -> node.cluster < 0 }
            .forEach { node ->
                node.cluster = clusterCount++
                clusterNode(node, 0)
            }
    }

    private fun clusterNode(node: KDTreeNode, depth: Int) {
        for (item in gridIndex) {
            val key = Index3D(node.key.toVector() + item.toVector())
            findNode(root!!, key)
                ?.takeIf { it.cluster < 0 }
                ?.let {
                    it.cluster = node.cluster
                    clusterNode(it, depth + 1)
                }
        }
    }

    /** 查找节点 */
    private tailrec fun findNode(subRoot: KDTreeNode, key: Index3D): KDTreeNode? =
        when {
            subRoot.isLeaf                             -> subRoot.takeIf { it.key == key }
            key[subRoot.pivotDim] < subRoot.pivotValue -> findNode(subRoot.children!!.first, key)
            else                                       -> findNode(subRoot.children!!.second, key)
        }

    /** 查找聚类 */
    fun getCluster(pose: Vector3D) =
        findNode(root!!, Index3D(pose / size))?.cluster ?: -1

    private fun insertNode(
        parent: KDTreeNode?,
        node: KDTreeNode?,
        key: Index3D,
        value: Double
    ): KDTreeNode {
        if (node == null)
            return KDTreeNode(
                    isLeaf = true,
                    depth = if (parent == null) 0 else (parent.depth + 1),
                    key = key,
                    value = value
            ).also {
                nodes.add(it)
                ++leafCount
            }

        if (node.isLeaf) {
            if (key == node.key) {
                node.value += value
            } else {
                node.pivotDim = key mostDifferentIndexWith node.key
                node.pivotValue = (key[node.pivotDim] + node.key[node.pivotDim]) / 2.0
                node.children =
                    if (key[node.pivotDim] < node.pivotValue)
                        Pair(first = insertNode(node, null, key, value),
                             second = insertNode(node, null, node.key, node.value))
                    else Pair(first = insertNode(node, null, node.key, node.value),
                              second = insertNode(node, null, key, value))
                node.isLeaf = false
                --leafCount
            }
        } else {
            if (key[node.pivotDim] < node.pivotValue)
                insertNode(node, node.children!!.first, key, value)
            else
                insertNode(node, node.children!!.second, key, value)
        }
        return node
    }

    private companion object {
        val gridIndex =
            sequence {
                for (x in -1..1)
                    for (y in -1..1)
                        for (z in -1..1)
                            yield(Index3D(x, y, z))
            }.toList()
    }
}
