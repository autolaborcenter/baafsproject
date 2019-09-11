package cn.autolabor.balltree

/**
 * 二叉球树
 */
sealed class Tree<T>(val value: T, val distance: (T, T) -> Double) {
    class Leaf<T>(value: T,
                  distance: (T, T) -> Double
    ) : Tree<T>(value, distance)

    class SingleBranch<T>(value: T,
                          val radius: Double,
                          val child: Tree<T>,
                          distance: (T, T) -> Double
    ) : Tree<T>(value, distance)

    class DoubleBranch<T>(value: T,
                          val radius: Double,
                          val left: Tree<T>,
                          val right: Tree<T>,
                          distance: (T, T) -> Double
    ) : Tree<T>(value, distance)

    fun neighbors(k: Int) {

    }

    companion object {
        /**
         * 从点集 [set] 构建球树
         */
        fun <T> build(set: Set<T>, distance: (T, T) -> Double) =
            set.firstOrNull()
                ?.let { root -> build(root, set.drop(1).associateWith { distance(it, root) }, distance) }

        private fun <T> build(root: T, map: Map<T, Double>, distance: (T, T) -> Double): Tree<T> {
            val buffer = map.keys.toHashSet()
            val (left, r) = map.maxBy { (_, d) -> d }
                            ?: return Leaf(root, distance)
            buffer.remove(left)
            val dLeft = buffer.associateWith { distance(it, left) }

            val right = dLeft.maxBy { (_, d) -> d }?.key
                        ?: return SingleBranch(root, r, Leaf(left, distance), distance)
            buffer.remove(right)
            val dRight = buffer.associateWith { distance(it, right) }
                .filter { (key, d) -> dLeft.getValue(key) > d }

            buffer.clear()
            return DoubleBranch(root, r,
                                build(left, dLeft.filterKeys { it !in dRight }, distance),
                                build(right, dRight, distance),
                                distance)
        }
    }
}

