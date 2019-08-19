package org.mechdancer

import cn.autolabor.pm1.Resource
import cn.autolabor.pm1.sdk.PM1
import org.mechdancer.dependency.must
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.remote.resources.MulticastSockets
import kotlin.concurrent.thread

fun main() {
    val remote = remoteHub("baafs test")
    val lidar = Resource { odometry ->
        println(odometry)
        remote.paint("odometry", odometry.x, odometry.y, odometry.theta)
    }
    PM1.locked = false
    PM1.setCommandEnabled(false)
    remote.openAllNetworks()
    println("remote launched on ${remote.components.must<MulticastSockets>().address}")
    thread { while (true) remote() }
    while (true) lidar()
}
