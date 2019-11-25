package org.mechdancer

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class YChannel<T>(context: CoroutineContext = EmptyCoroutineContext, size: Int = 2) {
    private val _input = Channel<T>(Channel.CONFLATED)
    private val _outputs = List(size) { Channel<T>(Channel.CONFLATED) }

    val input: SendChannel<T> get() = _input
    val outputs: List<ReceiveChannel<T>> get() = _outputs

    init {
        GlobalScope.launch(context) {
            for (item in _input) for (output in _outputs) output.send(item)
            for (output in _outputs) output.close()
        }
    }
}
