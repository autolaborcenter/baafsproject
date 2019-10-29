package org.mechdancer

import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.exceptions.ExceptionMessage.Occurred
import org.mechdancer.exceptions.ExceptionMessage.Recovered
import org.mechdancer.exceptions.RecoverableException
import java.util.concurrent.atomic.AtomicLong

class WatchDog(private val timeout: Long) {
    private val i = AtomicLong(0)

    suspend fun feed(): Boolean {
        val mark = i.incrementAndGet()
        delay(timeout)
        return mark != i.get()
    }

    suspend fun feedOrThrowTo(
        exceptions: SendChannel<ExceptionMessage>,
        exception: RecoverableException
    ) {
        exceptions.send(Recovered(exception))
        if (!feed()) exceptions.send(Occurred(exception))
    }
}
