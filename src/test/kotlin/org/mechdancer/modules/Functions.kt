package org.mechdancer.modules

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking

fun CoroutineScope.await() {
    runBlocking { this@await.coroutineContext[Job]?.join() }
}
