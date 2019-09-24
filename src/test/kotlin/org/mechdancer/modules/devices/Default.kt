package org.mechdancer.modules.devices

import cn.autolabor.locator.ParticleFilter
import cn.autolabor.locator.ParticleFilterBuilder.Companion.particleFilter
import org.mechdancer.dependency.must
import org.mechdancer.paint
import org.mechdancer.paintFrame2
import org.mechdancer.paintFrame3
import org.mechdancer.remote.presets.RemoteHub
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.remote.resources.MulticastSockets
import org.mechdancer.remote.resources.Networks

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

    fun ParticleFilter.paintWith(remote: RemoteHub) {
        stepFeedback = { (measureWeight, particleWeight, _, _, eLocator, _) ->
            with(remote) {
                paint("定位权重", measureWeight)
                paint("粒子权重", particleWeight)
                paint("粒子滤波（定位标签）", eLocator.p.x, eLocator.p.y, eLocator.d.asRadian())
                paintFrame3("粒子群", particles.map { (odom, _) -> Triple(odom.p.x, odom.p.y, odom.d.value) })
                paintFrame2("粒子寿命", particles.mapIndexed { i, (_, n) -> i.toDouble() to n.toDouble() })
            }
        }
    }
}
