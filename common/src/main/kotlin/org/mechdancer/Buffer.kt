package org.mechdancer

class Buffer<T>(private val size: Long) {
    private val core = mutableListOf<T>()
    fun get(): List<T> = core
    fun update(block: (T?) -> T?) {
        block(core.lastOrNull())?.also { core += it }
        if (core.size > size) core.removeAt(0)
    }
}
