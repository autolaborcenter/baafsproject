package org.mechdancer.modules.devices

import cn.autolabor.locator.ParticleFilter
import cn.autolabor.locator.ParticleFilterBuilder.Companion.particleFilter
import kotlinx.coroutines.channels.Channel
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.dependency.must
import org.mechdancer.paint
import org.mechdancer.paintFrame2
import org.mechdancer.paintFrame3
import org.mechdancer.remote.presets.RemoteHub
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.remote.resources.MulticastSockets

object Default {
    fun <T> channel() = Channel<T>(Channel.CONFLATED)

    val remote by lazy {
        val it = remoteHub("path follower test")
        it.openAllNetworks()
        println("remote launched on ${it.components.must<MulticastSockets>().address}")
        it
    }

    val filter by lazy {
        particleFilter { locatorOnRobot = vector2DOf(-0.31, 0) }
            .apply { paintWith(remote) }
    }

    // remote?.run {
    //     paint("超声波定位", x, y)
    //     paint("里程计", ox, oy, theta)
    //     filtered?.let { (p, d) -> paint("粒子滤波", p.x, p.y, d.value) }
    // }

    private fun ParticleFilter.paintWith(remote: RemoteHub) {
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
