package org.mechdancer

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val channel = YChannel<Int>()
    launch {
        for (i in 1..100) {
            channel.input.send(i)
            delay(500L)
        }
    }
    channel.outputs.forEachIndexed { k, output ->
        launch { for (i in output) println("$k received $i") }
    }
}
