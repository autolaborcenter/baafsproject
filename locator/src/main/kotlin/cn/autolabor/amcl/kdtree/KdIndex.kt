package cn.autolabor.amcl.kdtree

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

internal class KdIndex(values: List<Int>) : List<Int> by values {
    private val times get() = kPowers[size]!!

    init {
        require(values.isNotEmpty())

        var tn = 1
        kPowers.getOrPut(size) { (1..size).map { tn.also { tn *= 3 } }.asReversed() }
    }

    infix fun mostDifferentDimWith(others: KdIndex) =
        zip(others) { a, b -> abs(a - b) }
            .withIndex()
            .maxByOrNull { (_, value) -> value }!!
            .index

    fun neighbors(): List<KdIndex> =
        (1 until times.first()).map { i ->
            KdIndex(mapIndexed { k, it -> it + delta[i / times[k] % 3] })
        }

    companion object {
        fun indexOf(vararg values: Int) = KdIndex(values.toList())

        private val delta = listOf(0, -1, +1)
        private val kPowers =
            ConcurrentHashMap<Int, List<Int>>()
                .apply { this[1] = listOf(1) }
    }
}
