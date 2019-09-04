package org.mechdancer

import cn.autolabor.pm1.Resource
import cn.autolabor.pm1.sdk.PM1
import cn.autolabor.transform.Transformation
import org.mechdancer.PathFollowerModule.Coordination
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.geometry.angle.toRad

fun main() {
    val module = PathFollowerModule()
    val pm1 = Resource { odometry ->
        val p = vector2DOf(odometry.x, odometry.y)
        val d = odometry.theta.toRad()
        with(module) {
            system.cleanup(Coordination.BaseLink to Coordination.Map)
            system[Coordination.BaseLink to Coordination.Map] = Transformation.fromPose(p, d)
            recordNode(p)
            remote.paint("odometry", p.x, p.y, d.asRadian())
        }
    }

    // launch pm1
    PM1.locked = false
    PM1.setCommandEnabled(false)
    launchBlocking { pm1() }
    // launch parser
    module.blockParse()
    pm1.close()
}
