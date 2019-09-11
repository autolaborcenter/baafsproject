package org.mechdancer.modules

import cn.autolabor.Stamped
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
    private val remote: RemoteHub? = Default.remote,
    private val filter: ParticleFilter = Default.filter,
    private val callback: (Stamped<Odometry>) -> Unit = {}
) : Closeable {
    init {
        remote?.run {
            filter.stepFeedback = { (measureWeight, particleWeight, _, _, eLocator, _) ->
                paint("定位权重", measureWeight)
                paint("粒子权重", particleWeight)
                paint("粒子滤波（定位标签）", eLocator.p.x, eLocator.p.y, eLocator.d.asRadian())
                paintFrame3("粒子群", filter.particles.map { (odom, _) -> Triple(odom.p.x, odom.p.y, odom.d.value) })
                paintFrame2("粒子寿命", filter.particles.mapIndexed { i, (_, n) -> i.toDouble() to n.toDouble() })
            }
        }
    }

    private var running = true
    private val marvelmind = Resource { time, x, y ->
        val (_, _, _, ox, oy, theta) = PM1.odometry

        val now = System.currentTimeMillis()
        val filtered = Stamped(now, Odometry(vector2DOf(ox, oy), theta.toRad()))
            .let {
                filter.measureHelper(Stamped(time, vector2DOf(x, y)))
                filter.measureMaster(it)
                filter[it]
            }
            ?.also { callback(Stamped(now, it)) }

        remote?.run {
            paint("超声波定位", x, y)
            paint("里程计", ox, oy, theta)
            filtered?.let { (p, d) -> paint("粒子滤波", p.x, p.y, d.value) }
        }
    }

    fun marvelmindBlockTask() {
        while (running) marvelmind()
    }

    override fun close() {
        running = false
        marvelmind.close()
    }
}
