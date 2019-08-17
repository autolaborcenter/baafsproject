package org.mechdancer

import com.faselase.Resource
import org.mechdancer.dependency.must
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.remote.protocol.writeEnd
import org.mechdancer.remote.resources.MulticastSockets
import org.mechdancer.remote.resources.UdpCmd
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.concurrent.thread
import kotlin.math.cos
import kotlin.math.sin

fun main() {
    val remote = remoteHub("baafs test")
    val lidar = Resource { _, _, list ->
        println(list.size)
        ByteArrayOutputStream()
            .apply {
                writeEnd("faselase")
                DataOutputStream(this).apply {
                    writeByte(0)
                    writeInt(list.size)
                    writeByte(1)
                    for ((rho, theta) in list) {
                        writeDouble(rho * cos(theta))
                        writeDouble(rho * sin(theta))
                    }
                }
            }
            .toByteArray()
            .let { remote.broadcast(UdpCmd.TOPIC_MESSAGE, it) }
    }
    remote.openAllNetworks()
    println("remote launched on ${remote.components.must<MulticastSockets>().address}")
    thread { while (true) remote() }
    while (true) lidar()
}
