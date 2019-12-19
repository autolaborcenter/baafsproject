package org.mechdancer.vectorgrid

import org.mechdancer.algebra.core.Vector
import org.mechdancer.algebra.function.vector.div
import org.mechdancer.algebra.function.vector.plus
import org.mechdancer.algebra.implement.vector.listVectorOfZero

/** 平面格 */
class PlaneIndex(values: List<Int>)
    : GridIndex<PlaneIndex>,
      List<Int> by values {
    init {
        require(isNotEmpty())
    }

    /** 构造邻域列表 */
    override val neighbors: Set<PlaneIndex>
        get() {
            val times = times3()
            return (1 until times.first() * 3)
                .map { i -> PlaneIndex(mapIndexed { k, it -> it + dimDelta[i / times[k] % 3] }) }
                .toSet()
        }

    override fun toString() =
        rowView()

    override fun equals(other: Any?) =
        this === other || (other is PlaneIndex && elementEquals(other))

    override fun hashCode() =
        listHash()

    companion object {
        fun <T : Vector> VectorGird<T, PlaneIndex>.getSamplePoints(
            blockSize: T,
            block: (Set<PlaneIndex>) -> Boolean
        ) = regionMap.regions
            .mapNotNull { region ->
                region
                    .takeUnless(Collection<*>::isEmpty)
                    ?.takeIf(block)
                    ?.map(grids::getValue)
                    ?.map { set ->
                        set.fold(listVectorOfZero(blockSize.dim))
                        { sum, it -> sum + it } / set.size
                    }
            }
    }
}
