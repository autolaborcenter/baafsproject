package org.mechdancer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** 协程看门狗 */
class WatchDog(
    scope: CoroutineScope = GlobalScope,
    private val timeout: Long,
    private val block: suspend () -> Unit
) : CoroutineScope by scope {
    private val lastFeed = AtomicLong(0)
    private val job = AtomicReference(launch { })

    fun feed() {
        lastFeed.set(System.currentTimeMillis())
        job.updateAndGet { last ->
            last.takeIf { it.isActive }
                ?: launch {
                    delay(timeout)
                    while (true)
                        (lastFeed.get() + timeout - System.currentTimeMillis())
                            .takeIf { it > 0 }
                            ?.let { delay(it) }
                            ?: break
                    block()
                }
        }
    }
}
