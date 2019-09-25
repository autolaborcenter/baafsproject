package org.mechdancer.modules

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import org.mechdancer.SimpleLogger
import org.mechdancer.common.Velocity.Companion.velocity
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.dependency.must
import org.mechdancer.remote.modules.multicast.multicastListener
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.remote.protocol.SimpleInputStream
import org.mechdancer.remote.resources.MulticastSockets
import org.mechdancer.remote.resources.Networks
import java.io.DataInputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

object Default {
    private val commands_ = Channel<NonOmnidirectional>(Channel.CONFLATED)
    val commands: ReceiveChannel<NonOmnidirectional> get() = commands_

    val remote by lazy {
        remoteHub("simulator") {
            inAddition {
                multicastListener { _, _, payload ->
                    if (payload.size == 16)
                        GlobalScope.launch {
                            val stream = DataInputStream(SimpleInputStream(payload))
                            @Suppress("BlockingMethodInNonBlockingContext")
                            commands_.send(velocity(stream.readDouble(), stream.readDouble()))
                        }
                }
            }
        }.apply {
            openAllNetworks()
            println("simulator open ${components.must<Networks>().view.size} networks on ${components.must<MulticastSockets>().address}")
            thread(isDaemon = true) { while (true) invoke() }
        }
    }

    val loggers = ConcurrentHashMap<String, SimpleLogger>()
}
