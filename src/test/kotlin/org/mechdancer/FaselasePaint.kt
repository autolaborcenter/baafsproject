package org.mechdancer

import cn.autolabor.pm1.sdk.PM1
import com.faselase.Resource
import org.mechdancer.dependency.must
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.remote.resources.MulticastSockets
import kotlin.math.cos
import kotlin.math.sin

fun main() {
    val remote = remoteHub("faselase test")
    val faselase = Resource { _, _, list ->
        println(list.size)
        remote.paintFrame2("faselase",
                           list.map { (rho, theta) ->
                               rho * cos(theta) to rho * sin(theta)
                           })
    }
    // launch faselase
    PM1.locked = false
    PM1.setCommandEnabled(false)
    launchBlocking { faselase() }
    // launch network
    remote.openAllNetworks()
    println("remote launched on ${remote.components.must<MulticastSockets>().address}")
    while (true) remote()
}
