package org.mechdancer.datagrid

import org.mechdancer.algebra.core.Vector
import org.mechdancer.algebra.function.vector.div

/** 平面格 */
class PlaneIndex(values: List<Int>) : GridIndex<PlaneIndex>,
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
        fun Vector.toPlaneIndex(block: Vector) =
            PlaneIndex((this / block).toList().map(Number::toInt))
    }
}
