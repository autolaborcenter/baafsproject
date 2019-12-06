package cn.autolabor.amcl.kdtree

internal sealed class KdTreeNode {
    abstract val key: Index3D
    abstract val value: Double

    data class Leaf(
        override val key: Index3D,
        override val value: Double
    ) : KdTreeNode()

    data class Branch(
        override val key: Index3D,
        override val value: Double,
        val pivotDim: Int,
        val pivotValue: Double,
        val left: KdTreeNode,
        val right: KdTreeNode
    ) : KdTreeNode()

    companion object {
        // 查找节点
        tailrec fun findNode(subRoot: KdTreeNode, key: Index3D): Leaf? =
            when (subRoot) {
                is Leaf   -> subRoot.takeIf { it.key == key }
                is Branch ->
                    if (key[subRoot.pivotDim] < subRoot.pivotValue)
                        findNode(subRoot.left, key)
                    else
                        findNode(subRoot.right, key)
            }
    }
}
