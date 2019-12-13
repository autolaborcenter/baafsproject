package org.mechdancer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@ObsoleteCoroutinesApi
class YChannel<T>(
    context: CoroutineContext = EmptyCoroutineContext,
    size: Int = 2
) : SendChannel<T> {
    private val input: SendChannel<T> =
        GlobalScope.actor(context = context, capacity = Channel.CONFLATED) {
            for (item in this) for (output in _outputs) output.send(item)
            for (output in _outputs) output.close()
        }

    private val _outputs = List(size) { Channel<T>(Channel.CONFLATED) }
    val outputs: List<ReceiveChannel<T>> get() = _outputs

    @ExperimentalCoroutinesApi
    override val isClosedForSend
        get() = input.isClosedForSend

    @ExperimentalCoroutinesApi
    override val isFull
        get() = input.isFull

    override val onSend
        get() = input.onSend

    override fun close(cause: Throwable?) =
        input.close()

    @ExperimentalCoroutinesApi
    override fun invokeOnClose(handler: (cause: Throwable?) -> Unit) =
        input.invokeOnClose(handler)

    override fun offer(element: T) =
        input.offer(element)

    override suspend fun send(element: T) =
        input.send(element)
}
