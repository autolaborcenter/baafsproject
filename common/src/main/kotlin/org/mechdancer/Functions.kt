package org.mechdancer

import kotlinx.coroutines.channels.Channel

/** 构造发送无阻塞的通道 */
fun <T> channel() = Channel<T>(Channel.CONFLATED)
