package cn.autolabor.amcl.kdtree

import cn.autolabor.amcl.kdtree.KdTreeNode.Branch
import cn.autolabor.amcl.kdtree.KdTreeNode.Companion.findNode
import cn.autolabor.amcl.kdtree.KdTreeNode.Leaf
import org.mechdancer.algebra.implement.vector.Vector3D
import kotlin.math.PI
import kotlin.math.roundToInt

/** k 维树 */
class KdTree {
    private val leaves = hashMapOf<Leaf, Int>()
    private var root: KdTreeNode? = null

    private val blockSize = .1
    private val angleBlockCount = 36
    private val angleBlockSize = 2 * PI / angleBlockCount

    val leavesCount get() = leaves.size

    /** 插入节点 */
    fun insert(pose: Vector3D, value: Double) {
        val key = pose.sample()
        root = root?.let { insertNode(it, key, value) }
               ?: leaf(key, value)
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

    /** 查找聚类 */
    fun getCluster(pose: Vector3D) =
        findNode(root!!, pose.sample())
            ?.let { leaf -> leaves[leaf] }
        ?: -1

    /** 清空 */
    fun clear() {
        root = null
        leaves.clear()
    }

    // 构造叶子
    private fun leaf(key: Index3D, value: Double) =
        Leaf(key, value).also { leaves[it] = -1 }

    // 采样
    private fun Vector3D.sample(): Index3D {
        val (x, y, theta) = this
        return Index3D((x / blockSize).roundToInt(),
                       (y / blockSize).roundToInt(),
                       (theta / angleBlockSize).roundToInt() % angleBlockCount)
    }

    // 插入节点
    private tailrec fun insertNode(
        node: KdTreeNode,
        key: Index3D,
        value: Double
    ): KdTreeNode = when (node) {
        is Leaf   -> {
            leaves.remove(node)
            if (key == node.key)
                leaf(key, node.value + value)
            else {
                val pivotDim = key mostDifferentDimWith node.key
                val pivotValue = (key[pivotDim] + node.key[pivotDim]) / 2.0
                val new = leaf(key, value)
                val copy = leaf(node.key, node.value)

                if (key[pivotDim] > pivotValue)
                    Branch(node.key, node.value,
                           pivotDim, pivotValue,
                           new, copy)
                else
                    Branch(node.key, node.value,
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

    // 计算聚类
    private fun clusterNode(node: KdTreeNode) {
        for ((x, y, z) in node.key.neighbors())
            findNode(root!!, Index3D(x, y, z % angleBlockCount))
                ?.takeIf { leaves[it]!! < 0 }
                ?.let {
                    leaves[it] = leaves[node]!!
                    clusterNode(it)
                }
    }
}
