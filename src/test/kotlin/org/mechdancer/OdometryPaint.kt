package org.mechdancer

import cn.autolabor.pm1.Resource
import cn.autolabor.pm1.sdk.PM1
import org.mechdancer.dependency.must
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.remote.resources.MulticastSockets

fun main() {
    val remote = remoteHub("baafs test")
    val pm1 = Resource { odometry ->
        println(odometry)
        remote.paint("odometry", odometry.x, odometry.y, odometry.theta)
    }
    // launch pm1
    PM1.locked = false
    PM1.setCommandEnabled(false)
    launchBlocking { pm1() }
    // launch network
    remote.openAllNetworks()
    println("remote launched on ${remote.components.must<MulticastSockets>().address}")
    while (true) remote()
}
