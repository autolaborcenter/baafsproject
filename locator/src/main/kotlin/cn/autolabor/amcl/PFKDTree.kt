package cn.autolabor.amcl

import org.mechdancer.algebra.function.vector.div
import org.mechdancer.algebra.function.vector.minus
import org.mechdancer.algebra.function.vector.plus
import org.mechdancer.algebra.implement.vector.Vector3D
import org.mechdancer.algebra.implement.vector.vector3DOf
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor

data class KDTreeNode(
    var leaf: Boolean,
    var depth: Int,
    var key: Vector3D,
    var value: Double,
    var pivotDim: Int = -1,
    var pivotValue: Double = 0.0,
    var cluster: Int = -1,
    var children: Pair<KDTreeNode, KDTreeNode>? = null
)

data class KdTree(
    var size: Vector3D = Vector3D(0.1, 0.1, 10 * PI / 180),
    var root: KDTreeNode? = null,
    var nodeMaxCount: Int,
    var nodes: MutableList<KDTreeNode> = mutableListOf(),
    var leafCount: Int = 0
) {
    val maxYaw = floor(PI / size.z)
    val minYaw = floor(-PI / size.z)
}


fun kdTreeClear(self: KdTree): Unit {
    self.root = null
    self.leafCount = 0
    self.nodes.clear()
}

fun Vector3D.forEach(block: (Double) -> Double) = Vector3D(block(x), block(y), block(z))
fun Vector3D.maxIndex() = if (x > y) (if (x > z) 0 else 2) else (if (y > z) 1 else 2)

fun kdTreeInsert(self: KdTree, pose: Vector3D, value: Double): Unit {
    val key: Vector3D = (pose / self.size).forEach(::floor)
    self.root = kdTreeInsertNode(self, null, self.root, key, value)
}

fun kdTreeInsertNode(self: KdTree, parent: KDTreeNode?, node: KDTreeNode?, key: Vector3D, value: Double): KDTreeNode =
    node?.apply {
        if (this.leaf) {
            if (key == this.key) {
                this.value += value
            } else {
                this.pivotDim = (key - this.key).forEach(::abs).maxIndex()
                this.pivotValue = (key[this.pivotDim] + this.key[this.pivotDim]) / 2
                this.children = if (key[this.pivotDim] < this.pivotValue) Pair(
                        first = kdTreeInsertNode(self, this, null, key, value),
                        second = kdTreeInsertNode(self, this, null, this.key, this.value)
                )
                else Pair(
                        first = kdTreeInsertNode(self, this, null, this.key, this.value),
                        second = kdTreeInsertNode(self, this, null, key, value)
                )
                this.leaf = false
                self.leafCount--
            }
        } else {
            if (key[this.pivotDim] < this.pivotValue) {
                kdTreeInsertNode(self, this, this.children!!.first, key, value)
            } else {
                kdTreeInsertNode(self, this, this.children!!.second, key, value)
            }
        }
    } ?: KDTreeNode(
            leaf = true,
            depth = if (parent == null) 0 else (parent.depth + 1),
            key = key,
            value = value
    ).also {
        self.nodes.add(it)
        self.leafCount++
    }

fun kdTreeCluster(self: KdTree): Unit {
    val queue: MutableList<KDTreeNode> = mutableListOf()
    self.nodes.filter { it.leaf }.forEach {
        it.cluster = -1
        queue.add(it)
    }
    var clusterCount = 0
    for (node in queue.asReversed()) {
        if (node.cluster < 0) {
            node.cluster = clusterCount++
            kdTreeClusterNode(self, node, 0)
        }
    }
}

val gridIndex = sequence { for (x in -1..1) for (y in -1..1) for (z in -1..1) yield(vector3DOf(x, y, z)) }.toList()

fun kdTreeClusterNode(self: KdTree, node: KDTreeNode, depth: Int) {
    for (item in gridIndex) {
        val key = node.key + item
        kdTreeFindNode(self, self.root!!, key)
            ?.takeIf { it.cluster < 0 }
            ?.run {
                this.cluster = node.cluster
                kdTreeClusterNode(self, this, depth + 1)
            }
    }
}

fun kdTreeFindNode(self: KdTree, node: KDTreeNode, key: Vector3D): KDTreeNode? {
    return when {
        node.leaf                            -> if (key == node.key) node else null
        key[node.pivotDim] < node.pivotValue -> kdTreeFindNode(self, node.children!!.first, key)
        else                                 -> kdTreeFindNode(self, node.children!!.second, key)
    }
}

fun kdTreeGetCluster(self: KdTree, pose: Vector3D) =
    kdTreeFindNode(self, self.root!!, (pose / self.size).forEach(::floor))?.cluster ?: -1



