package org.mechdancer

import com.faselase.Resource
import org.mechdancer.dependency.must
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.remote.resources.MulticastSockets
import kotlin.concurrent.thread
import kotlin.math.cos
import kotlin.math.sin

fun main() {
    val remote = remoteHub("baafs test")
    val lidar = Resource { _, _, list ->
        println(list.size)
        remote.paint("faselase",
                     list.map { (rho, theta) ->
                         rho * cos(theta) to rho * sin(theta)
                     })
    }
    remote.openAllNetworks()
    println("remote launched on ${remote.components.must<MulticastSockets>().address}")
    thread { while (true) remote() }
    while (true) lidar()
}
