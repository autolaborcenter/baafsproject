package org.mechdancer

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class YChannel<T>(context: CoroutineContext = EmptyCoroutineContext, size: Int = 2) {
    private val _input = Channel<T>(Channel.CONFLATED)
    private val _outputs = List(size) { Channel<T>(Channel.CONFLATED) }

    val input: SendChannel<T> get() = _input
    val outputs: List<ReceiveChannel<T>> get() = _outputs

    init {
        GlobalScope.launch(context) {
            for (item in _input) _outputs.forEach { it.send(item) }
            _outputs.forEach { it.close() }
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) = runBlocking {
            val channel = YChannel<Int>()
            launch {
                for (i in 1..100) {
                    channel.input.send(i)
                    delay(500L)
                }
            }
            channel.outputs.forEachIndexed { k, output ->
                launch { for (i in output) println("$i received $k") }
            }
        }
    }
}
