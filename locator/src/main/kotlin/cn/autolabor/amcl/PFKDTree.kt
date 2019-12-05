package cn.autolabor.amcl

import cn.autolabor.amcl.KDTreeNode.Branch
import cn.autolabor.amcl.KDTreeNode.Leaf
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

sealed class KDTreeNode {
    abstract val depth: Int
    abstract val key: Index3D
    abstract val value: Double

    var cluster = -1

    data class Leaf(
        override val depth: Int,
        override val key: Index3D,
        override val value: Double
    ) : KDTreeNode()

    data class Branch(
        override val depth: Int,
        override val key: Index3D,
        override val value: Double,
        val pivotDim: Int = -1,
        val pivotValue: Double = .0,
        var left: KDTreeNode,
        var right: KDTreeNode
    ) : KDTreeNode()
}

/** k 维树 */
class KdTree {
    private val leaves = hashSetOf<Leaf>()
    private val blockSize = Vector3D(.1, .1, 10 * PI / 180)
    private var root: KDTreeNode? = null

    val leavesCount get() = leaves.size

    /** 插入节点 */
    fun insert(pose: Vector3D, value: Double) {
        val key = Index3D(pose / blockSize)
        root = root?.let { insertNode(it, key, value) }
               ?: leaf(0, key, value)
    }

    /** 清空 */
    fun clear() {
        root = null
        leaves.clear()
    }

    /** 计算聚类 */
    fun cluster() {
        var clusterCount = 0
        leaves
            .onEach { it.cluster = -1 }
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

    /** 查找聚类 */
    fun getCluster(pose: Vector3D) =
        findNode(root!!, Index3D(pose / blockSize))?.cluster ?: -1

    private fun leaf(depth: Int, key: Index3D, value: Double) =
        Leaf(depth, key, value).also { assert(leaves.add(it)) }

    private tailrec fun insertNode(
        node: KDTreeNode,
        key: Index3D,
        value: Double
    ): KDTreeNode = when (node) {
        is Leaf   -> {
            leaves.remove(node)
            if (key == node.key)
                leaf(node.depth, key, node.value + value)
            else {
                val pivotDim = key mostDifferentIndexWith node.key
                val pivotValue = (key[pivotDim] + node.key[pivotDim]) / 2.0
                val new = leaf(node.depth + 1, key, value)
                val copy = leaf(node.depth + 1, node.key, node.value)
                if (key[pivotDim] > pivotValue)
                    Branch(node.depth, node.key, node.value,
                           pivotDim, pivotValue,
                           new, copy)
                else
                    Branch(node.depth, node.key, node.value,
                           pivotDim, pivotValue,
                           copy, new)
            }
        }
        is Branch ->
            if (key[node.pivotDim] < node.pivotValue)
                insertNode(node.left, key, value)
            else
                insertNode(node.right, key, value)
    }

    private companion object {
        /** 查找节点 */
        tailrec fun findNode(subRoot: KDTreeNode, key: Index3D): KDTreeNode? =
            when (subRoot) {
                is Leaf   -> subRoot.takeIf { it.key == key }
                is Branch ->
                    if (key[subRoot.pivotDim] < subRoot.pivotValue)
                        findNode(subRoot.left, key)
                    else
                        findNode(subRoot.right, key)
            }

        val gridIndex =
            sequence {
                for (x in -1..1)
                    for (y in -1..1)
                        for (z in -1..1)
                            yield(Index3D(x, y, z))
            }.toList()
    }
}
