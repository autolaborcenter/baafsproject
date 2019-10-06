package org.mechdancer

import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicLong

class WatchDog(private val timeout: Long) {
    private val i = AtomicLong(0)

    suspend fun feed(): Boolean {
        val mark = i.incrementAndGet()
        delay(timeout)
        return mark != i.get()
    }
}
