package cn.autolabor.amcl.kdtree

import kotlin.math.abs

internal class Index3D(x: Int, y: Int, z: Int)
    : List<Int> by listOf(x, y, z) {

    infix fun mostDifferentDimWith(others: Index3D) =
        zip(others) { a, b -> abs(a - b) }
            .withIndex()
            .maxBy { (_, value) -> value }!!
            .index

    fun neighbors() =
        sequence {
            for (dx in -1..1)
                for (dy in -1..1)
                    for (dz in -1..1)
                        yield(Index3D(get(0) + dx, get(1) + dy, get(2) + dz))
        }.filterNot { it == this }
}
