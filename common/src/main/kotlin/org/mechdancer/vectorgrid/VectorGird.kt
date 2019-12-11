package org.mechdancer.vectorgrid

import org.mechdancer.algebra.core.Vector
import org.mechdancer.algebra.function.vector.div

class VectorGird<T : Vector>(
    private val blockSize: T,
    list: Iterable<T>
) {
    init {
        require(list.map { it.dim }.toSet().single() == blockSize.dim)
    }

    val grids by lazy { list.groupBy(this::indexOf) }
    val regionMap by lazy { RegionMap(grids.keys) }

    /** 获取点对应的区域 */
    fun getRegion(key: T) =
        regionMap.run { indices[indexOf(key)]?.let { regions[it] } }

    /** 获取满足条件的类中的点 */
    fun getPoints(block: (Set<IndexN>) -> Boolean) =
        regionMap.regions
            .mapNotNull { region ->
                region.takeIf(block)?.flatMap(grids::getValue)
            }

    /** 区域正反映射 */
    class RegionMap internal constructor(grids: Set<IndexN>) {
        /** 区域号对应的点集 */
        val regions: List<Set<IndexN>> =
            sequence {
                val rest = grids.toHashSet()
                while (true) {
                    val root = rest.poll() ?: break
                    val region = hashSetOf(root)
                    val edges = root.neighbors().toHashSet()
                    while (true) {
                        val edge = edges.poll() ?: break
                        if (edge in rest) {
                            rest.remove(edge)
                            region.add(edge)
                            edges.addAll(edge.neighbors().filter { it !in region })
                        }
                    }
                    yield(region)
                }
            }.toList()

        /** 点到区域号的映射 */
        val indices: Map<IndexN, Int> =
            regions
                .withIndex()
                .flatMap { (i, set) -> set.map { it to i } }
                .toMap()

        private companion object {
            fun <T> MutableSet<T>.poll() = firstOrNull()?.also { remove(it) }
        }
    }

    private fun indexOf(vector: T) =
        (vector / blockSize).toList().map(Number::toInt).let(::IndexN)
}
