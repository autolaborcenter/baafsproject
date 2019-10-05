package org.mechdancer.baafs

import com.faselase.Resource
import org.mechdancer.dependency.must
import org.mechdancer.paintVectors
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.remote.resources.MulticastSockets
import org.mechdancer.remote.resources.Networks
import kotlin.concurrent.thread

fun main() {
    val remote by lazy {
        remoteHub("simulator").apply {
            openAllNetworks()
            println("simulator open ${components.must<Networks>().view.size} networks on ${components.must<MulticastSockets>().address}")
            thread(isDaemon = true) { while (true) invoke() }
        }
    }

    var time = System.currentTimeMillis()
    val faselase = Resource { list ->
        val now = System.currentTimeMillis()
        if (now - time > 20) {
            time = now
            remote.paintVectors("faselase", list.map { it.data.toVector2D() })
        }
    }
    while (true) faselase()
}
