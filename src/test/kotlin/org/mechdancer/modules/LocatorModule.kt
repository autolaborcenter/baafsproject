package org.mechdancer.modules

import cn.autolabor.Stamped
import cn.autolabor.Stamped.Companion.stamp
import cn.autolabor.locator.ParticleFilter
import cn.autolabor.pm1.sdk.PM1
import cn.autolabor.utilities.Odometry
import com.marvelmind.Resource
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.paint
import org.mechdancer.paintFrame2
import org.mechdancer.paintFrame3
import org.mechdancer.remote.presets.RemoteHub
import java.io.Closeable

class LocatorModule(
    private val remote: RemoteHub,
    private val callback: (Odometry) -> Unit
) : Closeable {
    private var running = false

    private val filter = ParticleFilter(128)
    private val marvelmind = Resource { time, x, y ->
        filter.measureHelper(Stamped(time, vector2DOf(x, y)))
        locate()

        remote.paint("超声波定位", x, y)
    }

    fun marvelmindBlockTask() {
        while (running) marvelmind()
    }

    override fun close() {
        running = false
        marvelmind.close()
    }

    private fun locate() {
        val (_, _, _, x, y, theta) = PM1.odometry
        val inner = stamp(Odometry(vector2DOf(x, y), theta.toRad()))
        filter.measureMaster(inner)
        filter[inner]?.also(callback)?.also { (p, d) -> remote.paint("filter", p.x, p.y, d.value) }

        remote.paint("里程计", x, y, theta)
        filter.stepState.let { (measureWeight, particleWeight, _, _) ->
            remote.paint("定位权重", measureWeight)
            remote.paint("粒子权重", particleWeight)
        }
        remote.paintFrame3("粒子群",
                           filter.particles.map { (odom, _) -> Triple(odom.p.x, odom.p.y, odom.d.value) })
        remote.paintFrame2("粒子寿命",
                           filter.particles.mapIndexed { i, (_, n) -> i.toDouble() to n.toDouble() })
    }
}
