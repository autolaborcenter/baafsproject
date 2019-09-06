package org.mechdancer.modules

import cn.autolabor.Stamped
import cn.autolabor.locator.ParticleFilter
import cn.autolabor.utilities.Odometry
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.paint
import org.mechdancer.paintFrame2
import org.mechdancer.paintFrame3
import org.mechdancer.remote.presets.RemoteHub
import java.io.Closeable

class LocatorModule(
    private val remote: RemoteHub,
    callback: (Odometry) -> Unit
) : Closeable {
    private var running = false

    private val filter = ParticleFilter(128)
    private val marvelmind = com.marvelmind.Resource { time, x, y ->
        remote.paint("marvelmind", x, y)
        filter.measureHelper(Stamped(time, vector2DOf(x, y)))
    }
    private val pm1 = cn.autolabor.pm1.Resource { (stamp, _, _, x, y, theta) ->
        val inner = Stamped(stamp, Odometry(vector2DOf(x, y), theta.toRad()))

        remote.paint("odometry", x, y, theta)
        filter.measureMaster(inner)

        val (measureWeight, particleWeight) = filter.stepState
        remote.paint("定位权重", measureWeight)
        remote.paint("粒子权重", particleWeight)

        filter[inner]
            ?.also(callback)
            ?.also { (p, d) -> remote.paint("filter", p.x, p.y, d.value) }
        remote.paintFrame3("particles",
                           filter.particles.map { (odom, _) -> Triple(odom.p.x, odom.p.y, odom.d.value) })
        remote.paintFrame2("life",
                           filter.particles.mapIndexed { i, (_, n) -> i.toDouble() to n.toDouble() })
    }

    fun marvelmindBlockTask() {
        while (running) marvelmind()
    }

    fun pm1BlockTask() {
        while (running) pm1()
    }

    override fun close() {
        running = false
        marvelmind.close()
        pm1.close()
    }
}
