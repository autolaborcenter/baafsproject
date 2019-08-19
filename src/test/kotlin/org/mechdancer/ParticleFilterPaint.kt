package org.mechdancer

import cn.autolabor.locator.ParticleFilter
import cn.autolabor.pm1.sdk.PM1
import cn.autolabor.utilities.Odometry
import cn.autolabor.utilities.time.Stamped
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.dependency.must
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.remote.resources.MulticastSockets
import kotlin.concurrent.thread

fun main() {
    val remote = remoteHub("baafs test")
    val filter = ParticleFilter(32)

    val marvelmind = com.marvelmind.Resource { time, x, y ->
        println("$x $y")
        remote.paint("marvelmind", x, y)

        filter.measureHelper(Stamped(time, vector2DOf(x, y)))
    }
    val pm1 = cn.autolabor.pm1.Resource { odometry ->
        val inner = Stamped(odometry.stamp,
                            Odometry(odometry.s,
                                     odometry.sa,
                                     vector2DOf(odometry.x, odometry.y),
                                     odometry.theta.toRad()))
        remote.paint("odometry", odometry.x, odometry.y, odometry.theta)

        filter.measureMaster(inner)
        filter[inner]?.let { (_, _, p, d) ->
            remote.paint("filter", p.x, p.y, d.value)
        }
    }

    PM1.locked = false
    PM1.setCommandEnabled(false)

    remote.openAllNetworks()
    println("remote launched on ${remote.components.must<MulticastSockets>().address}")

    thread { while (true) marvelmind() }
    thread { while (true) pm1() }
    while (true) remote()
}
