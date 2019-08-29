package org.mechdancer

import com.marvelmind.Resource
import org.mechdancer.dependency.must
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.remote.resources.MulticastSockets

fun main() {
    val remote = remoteHub("baafs test")
    val marvelmind = Resource { _, x, y ->
        println("$x $y")
        remote.paint("marvelmind", x, y)
    }
    // launch marvelmind
    launchBlocking { marvelmind() }
    // launch network
    remote.openAllNetworks()
    println("remote launched on ${remote.components.must<MulticastSockets>().address}")
    while (true) remote()
}
