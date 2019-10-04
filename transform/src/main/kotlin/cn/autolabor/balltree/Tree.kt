package cn.autolabor.balltree

/**
 * 二叉球树
 */
sealed class Tree<T> {
    abstract val value: T
    abstract val distance: (T, T) -> Double

    /** 叶子节点 */
    data class Leaf<T>(override val value: T,
                       override val distance: (T, T) -> Double
    ) : Tree<T>()

    /** 单枝节点 */
    data class SingleBranch<T>(override val value: T,
                               val radius: Double,
                               val child: Tree<T>,
                               override val distance: (T, T) -> Double
    ) : Tree<T>()

    /** 双枝节点 */
    data class DoubleBranch<T>(override val value: T,
                               val radius: Double,
                               val left: Tree<T>,
                               val right: Tree<T>,
                               override val distance: (T, T) -> Double
    ) : Tree<T>()

    /** 找到 [target] 在树中的最近邻 */
    fun neighborsOf(target: T): T =
        when (this) {
            is Leaf         -> value
            is SingleBranch -> {
                val (o, r, left, _) = this
                val d = distance(target, o)
                if (d < r) {
                    val dl = distance(target, left.value)
                }
                o
            }
            is DoubleBranch -> {
                val (o, r, left, right, _) = this
                val d = distance(target, o)
                o
            }
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
                                build(left, dLeft.filterKeys { it !in dRight && it != right }, distance),
                                build(right, dRight, distance),
                                distance)
        }
    }
}

