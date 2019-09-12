package org.mechdancer

import cn.autolabor.Odometry
import cn.autolabor.pm1.sdk.PM1
import cn.autolabor.transform.Transformation
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.modules.Coordination.Map
import org.mechdancer.modules.Coordination.Robot
import org.mechdancer.modules.Default
import org.mechdancer.modules.PathFollowerModule

fun main() {
    val remote = Default.remote
    val system = Default.system
    // 导航模块
    val follower = PathFollowerModule()
    PM1.initialize()
    PM1.locked = false
    PM1.setCommandEnabled(false)
    // 启动里程计资源
    launchBlocking {
        val (_, _, _, x, y, theta) = PM1.odometry
        val (p, d) = follower.offset plusDelta Odometry(vector2DOf(x, y), theta.toRad())
        system.cleanup(Robot to Map, System.currentTimeMillis() - 5000)
        system[Robot to Map] = Transformation.fromPose(p, d)
        follower.record(p)
        remote.paint("odometry", p.x, p.y, d.asRadian())
        Thread.sleep(100)
    }
    // launch parser
    follower.parseRepeatedly()
}
