package org.mechdancer.vectorgrid

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/** N 维格序号 */
class IndexN(values: List<Int>) : List<Int> by values {
    init {
        require(isNotEmpty())
        // 缓存 3 的幂次表
        powersLock.write {
            while (powers.size < size)
                powers += powers.last() * 3
        }
    }

    /** 构造邻域列表 */
    fun neighbors(): Set<IndexN> {
        val times = powersLock.read { powers.take(size) }.asReversed()
        return (1 until times.first() * 3).map { i ->
            IndexN(mapIndexed { k, it -> it + delta[i / times[k] % 3] })
        }.toSet()
    }

    override fun toString() =
        joinToString(" ", "(", ")")

    override fun equals(other: Any?) =
        this === other
        || (other is IndexN
            && this.zip(other) { a, b -> a == b }.all { it })

    override fun hashCode() =
        reduce { code, it -> code.hashCode() * 31 + it.hashCode() }

    private companion object {
        val delta = listOf(0, -1, +1)
        val powersLock = ReentrantReadWriteLock()
        val powers = mutableListOf(1)
    }
}
