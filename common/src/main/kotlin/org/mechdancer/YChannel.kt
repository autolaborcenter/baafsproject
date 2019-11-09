package org.mechdancer

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
