package org.mechdancer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking

/** 构造发送无阻塞的通道 */
fun <T> channel() = Channel<T>(Channel.CONFLATED)

/** 等待协程作用域中全部工作结束 */
fun CoroutineScope.await() {
    runBlocking { this@await.coroutineContext[Job]?.join() }
}
