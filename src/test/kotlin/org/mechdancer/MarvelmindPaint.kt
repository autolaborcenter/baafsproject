package org.mechdancer

import com.marvelmind.Resource
import org.mechdancer.dependency.must
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.remote.resources.MulticastSockets
import kotlin.concurrent.thread

fun main() {
    val remote = remoteHub("baafs test")
    val lidar = Resource { _, x, y ->
        println("$x $y")
        remote.paint("marvelmind", x, y)
    }
    remote.openAllNetworks()
    println("remote launched on ${remote.components.must<MulticastSockets>().address}")
    thread { while (true) remote() }
    while (true) lidar()
}
