package org.mechdancer.datagrid

class DataGird<T, I : GridIndex<I>>(
    val grids: Map<I, List<T>>,
    private val indexOf: (T) -> I
) {
    val regionMap by lazy { RegionMap(grids.keys) }

    /** 获取点对应的区域 */
    fun getRegion(key: T) =
        regionMap.run { indices[indexOf(key)]?.let { regions[it] } }

    /** 区域正反映射 */
    class RegionMap<I : GridIndex<I>>
    internal constructor(grids: Set<I>) {
        /** 区域号对应的点集 */
        val regions: List<Set<I>> =
            sequence {
                val rest = grids.toHashSet()
                while (true) {
                    val root = rest.poll() ?: break
                    val region = hashSetOf(root)
                    val edges = root.neighbors.toHashSet()
                    while (true) {
                        val edge = edges.poll() ?: break
                        if (edge in rest) {
                            rest.remove(edge)
                            region.add(edge)
                            edges.addAll(edge.neighbors.filter { it !in region })
                        }
                    }
                    yield(region)
                }
            }.toList()

        /** 点到区域号的映射 */
        val indices: Map<I, Int> =
            regions
                .withIndex()
                .flatMap { (i, set) -> set.map { it to i } }
                .toMap()

        private companion object {
            fun <T> MutableSet<T>.poll() = firstOrNull()?.also { remove(it) }
        }
    }

    companion object {
        fun <T, I : GridIndex<I>> Iterable<T>.toDataGrid(block: (T) -> I) =
            DataGird(groupBy(block), block)
    }
}
