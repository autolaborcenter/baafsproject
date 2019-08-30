package org.mechdancer

import com.marvelmind.Resource
import org.mechdancer.dependency.must
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.remote.resources.MulticastSockets
import java.net.InetSocketAddress

fun main() {
    val remote = remoteHub(name = "marvelmind test",
                           address = InetSocketAddress("238.88.8.100", 30000))
    var i = 0
    val marvelmind = Resource { _, x, y ->
        println("${++i}: $x $y")
        remote.paint("marvelmind", x, y)
    }
    // launch marvelmind
    launchBlocking { marvelmind() }
    // launch network
    remote.openAllNetworks()
    println("remote launched on ${remote.components.must<MulticastSockets>().address}")
    while (true) remote()
}
