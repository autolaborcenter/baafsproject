package org.mechdancer

import cn.autolabor.locator.ParticleFilter
import cn.autolabor.pm1.sdk.PM1
import cn.autolabor.utilities.Odometry
import cn.autolabor.utilities.time.Stamped
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.dependency.must
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.remote.protocol.writeEnd
import org.mechdancer.remote.resources.MulticastSockets
import org.mechdancer.remote.resources.UdpCmd.TOPIC_MESSAGE
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.concurrent.thread

fun main() {
    val remote = remoteHub("baafs test")
    val filter = ParticleFilter(32)

    val marvelmind = com.marvelmind.Resource { time, x, y ->
        filter.measureHelper(Stamped(time, vector2DOf(x, y)))
        println("$x $y")
        ByteArrayOutputStream()
            .apply {
                writeEnd("marvelmind")
                DataOutputStream(this).apply {
                    writeDouble(x)
                    writeDouble(y)
                }
            }
            .toByteArray()
            .let { remote.broadcast(TOPIC_MESSAGE, it) }
    }
    val pm1 = cn.autolabor.pm1.Resource { odometry ->
        val inner = Stamped(odometry.stamp,
                            Odometry(odometry.s,
                                     odometry.sa,
                                     vector2DOf(odometry.x, odometry.y),
                                     odometry.theta.toRad()))
        filter.measureMaster(inner)
        ByteArrayOutputStream()
            .apply {
                writeEnd("odometry")
                DataOutputStream(this).apply {
                    writeDouble(odometry.x)
                    writeDouble(odometry.y)
                    writeDouble(odometry.theta)
                }
            }
            .toByteArray()
            .let { remote.broadcast(TOPIC_MESSAGE, it) }
        filter[inner]
            ?.let { (_, _, p, d) ->
                ByteArrayOutputStream()
                    .apply {
                        writeEnd("filter")
                        DataOutputStream(this).apply {
                            writeDouble(p.x)
                            writeDouble(p.y)
                            writeDouble(d.value)
                        }
                    }
            }
            ?.toByteArray()
            ?.let { remote.broadcast(TOPIC_MESSAGE, it) }
    }

    PM1.locked = false
    PM1.setCommandEnabled(false)

    remote.openAllNetworks()
    println("remote launched on ${remote.components.must<MulticastSockets>().address}")

    thread { while (true) marvelmind() }
    thread { while (true) pm1() }
    while (true) remote()
}
