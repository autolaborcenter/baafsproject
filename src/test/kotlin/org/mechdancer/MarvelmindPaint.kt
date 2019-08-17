package org.mechdancer

import com.marvelmind.Resource
import org.mechdancer.dependency.must
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.remote.protocol.writeEnd
import org.mechdancer.remote.resources.MulticastSockets
import org.mechdancer.remote.resources.UdpCmd
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.concurrent.thread

fun main() {
    val remote = remoteHub("baafs test")
    val lidar = Resource { stamp, x, y ->
        println("$x $y")
        ByteArrayOutputStream()
            .apply {
                writeEnd("marvelmind")
                DataOutputStream(this).apply {
                    writeByte(2)
                    writeDouble(x)
                    writeDouble(y)
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
