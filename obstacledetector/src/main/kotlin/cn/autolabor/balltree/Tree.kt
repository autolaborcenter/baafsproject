package cn.autolabor.balltree

class Tree<T>(val node: Node<T>,
              val children: Pair<Tree<T>?, Tree<T>?> = null to null)

/**
 * 从点集 [set] 构建球树
 */
fun <T> build(set: Set<T>, distance: (T, T) -> Double) =
    set.firstOrNull()
        ?.let { root -> build(root, set.drop(1).associateWith { distance(it, root) }, distance) }

private fun <T> build(root: T, map: Map<T, Double>, distance: (T, T) -> Double): Tree<T> {
    val buffer = map.keys.toHashSet()
    val (left, r) = map.maxBy { (_, d) -> d }
                    ?: return Tree(Node(root, .0))
    buffer.remove(left)
    val dLeft = buffer.associateWith { distance(it, left) }

    val right = dLeft.maxBy { (_, d) -> d }?.key
                ?: return Tree(Node(root, r), Tree(Node(left, .0)) to null)
    buffer.remove(right)
    val dRight = buffer.associateWith { distance(it, right) }

    val gLeft = hashSetOf<T>()
    val gRight = hashSetOf<T>()
    for (p in buffer) {
        if (dLeft.getValue(p) < dRight.getValue(p))
            gLeft += p
        else
            gRight += p
    }
    buffer.clear()
    return Tree(Node(root, r),
                build(left, dLeft.filterKeys { it in gLeft }, distance) to
                    build(right, dRight.filterKeys { it in gRight }, distance))
}
