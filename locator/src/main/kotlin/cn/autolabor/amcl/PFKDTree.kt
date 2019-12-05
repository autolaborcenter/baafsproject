package cn.autolabor.amcl

import cn.autolabor.amcl.KDTreeNode.Branch
import cn.autolabor.amcl.KDTreeNode.Leaf
import org.mechdancer.algebra.function.vector.div
import org.mechdancer.algebra.implement.vector.Vector3D
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

    fun neighbors() =
        sequence {
            for (dx in -1..1)
                for (dy in -1..1)
                    for (dz in -1..1)
                        yield(Index3D(x + dx, y + dy, z + dz))
        }.filterNot { it == this }
}

sealed class KDTreeNode {
    abstract val depth: Int
    abstract val key: Index3D
    abstract val value: Double

    data class Leaf(
        override val depth: Int,
        override val key: Index3D,
        override val value: Double
    ) : KDTreeNode()

    data class Branch(
        override val depth: Int,
        override val key: Index3D,
        override val value: Double,
        val pivotDim: Int,
        val pivotValue: Double,
        val left: KDTreeNode,
        val right: KDTreeNode
    ) : KDTreeNode()
}

/** 查找节点 */
private tailrec fun findNode(subRoot: KDTreeNode, key: Index3D): Leaf? =
    when (subRoot) {
        is Leaf   -> subRoot.takeIf { it.key == key }
        is Branch ->
            if (key[subRoot.pivotDim] < subRoot.pivotValue)
                findNode(subRoot.left, key)
            else
                findNode(subRoot.right, key)
    }

/** k 维树 */
class KdTree {
    private val leaves = hashMapOf<Leaf, Int>()
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
        for (key in leaves.keys)
            leaves[key] = -1
        for ((leaf, cluster) in leaves)
            if (cluster < 0) {
                leaves[leaf] = clusterCount++
                clusterNode(leaf)
            }
    }

    private fun clusterNode(node: KDTreeNode) {
        for (key in node.key.neighbors())
            findNode(root!!, key)
                ?.takeIf { leaves[it]!! < 0 }
                ?.let {
                    leaves[it] = leaves[node]!!
                    clusterNode(it)
                }
    }

    /** 查找聚类 */
    fun getCluster(pose: Vector3D) =
        findNode(root!!, Index3D(pose / blockSize))
            ?.let { leaf -> leaves[leaf] }
        ?: -1

    /** 构造叶子 */
    private fun leaf(depth: Int, key: Index3D, value: Double) =
        Leaf(depth, key, value).also { leaves[it] = -1 }

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

//                if(Random.nextBoolean())
                if (key[pivotDim] < pivotValue)
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
}
