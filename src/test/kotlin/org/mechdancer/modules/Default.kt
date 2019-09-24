package org.mechdancer.modules

import cn.autolabor.locator.ParticleFilterBuilder.Companion.particleFilter
import org.mechdancer.SimpleLogger
import org.mechdancer.dependency.must
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.remote.resources.MulticastSockets
import org.mechdancer.remote.resources.Networks
import java.util.concurrent.ConcurrentHashMap

object Default {
    val remote by lazy {
        remoteHub("simulator").apply {
            openAllNetworks()
            println("simulator open ${components.must<Networks>().view.size} networks on ${components.must<MulticastSockets>().address}")
        }
    }

    val filter by lazy {
        particleFilter { maxAge = 100 }.apply { paintWith(remote) }
    }

    val loggers = ConcurrentHashMap<String, SimpleLogger>()
}
