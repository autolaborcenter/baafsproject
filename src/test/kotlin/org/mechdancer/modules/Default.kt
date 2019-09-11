package org.mechdancer.modules

import cn.autolabor.locator.ParticleFilterBuilder.Companion.particleFilter
import cn.autolabor.transform.TransformSystem
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.dependency.must
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.remote.resources.MulticastSockets

object Default {
    val remote by lazy {
        val it = remoteHub("path follower test")
        it.openAllNetworks()
        println("remote launched on ${it.components.must<MulticastSockets>().address}")
        it
    }

    val filter by lazy { particleFilter { locatorOnRobot = vector2DOf(-0.305, 0) } }

    val system by lazy { TransformSystem<Coordination>() }
}
