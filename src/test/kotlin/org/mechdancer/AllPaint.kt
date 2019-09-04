package org.mechdancer

import cn.autolabor.Stamped
import cn.autolabor.locator.ParticleFilter
import cn.autolabor.pm1.sdk.PM1
import cn.autolabor.transform.Transformation
import cn.autolabor.utilities.Odometry
import org.mechdancer.PathFollowerModule.Coordination
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.geometry.angle.toRad

fun main() {
    val module = PathFollowerModule()
    val filter = ParticleFilter(128)
    val marvelmind = com.marvelmind.Resource { time, x, y ->
        module.remote.paint("marvelmind", x, y)
        filter.measureHelper(Stamped(time, vector2DOf(x, y)))
    }
    val pm1 = cn.autolabor.pm1.Resource { (stamp, _, _, x, y, theta) ->
        val inner = Stamped(stamp, Odometry(vector2DOf(x, y), theta.toRad()))

        with(module) {
            remote.paint("odometry", x, y, theta)
            filter.measureMaster(inner)

            remote.paintFrame3("particles",
                               filter.particles.map { (odom, _) -> Triple(odom.p.x, odom.p.y, odom.d.value) })
            remote.paintFrame2("life", filter.particles.mapIndexed { i, (_, n) -> i.toDouble() to n.toDouble() })

            val (measureWeight, particleWeight) = filter.weightTemp
            remote.paint("定位权重", measureWeight)
            remote.paint("粒子权重", particleWeight)

            filter[inner]
                ?.also { (p, d) ->
                    system.cleanup(Coordination.BaseLink to Coordination.Map)
                    system[Coordination.BaseLink to Coordination.Map] = Transformation.fromPose(p, d)
                    recordNode(p)
                    remote.paint("filter", p.x, p.y, d.value)
                }
        }
    }

    // launch pm1
    PM1.locked = false
    PM1.setCommandEnabled(false)
    launchBlocking { pm1() }
    // launch marvelmind
    launchBlocking { marvelmind() }
    // launch parser
    module.blockParse()
    pm1.close()
}
