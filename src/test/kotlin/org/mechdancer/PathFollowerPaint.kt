package org.mechdancer

import cn.autolabor.Odometry
import cn.autolabor.core.server.ServerManager
import cn.autolabor.message.navigation.Msg2DOdometry
import cn.autolabor.message.navigation.Msg2DPose
import cn.autolabor.message.navigation.Msg2DTwist
import cn.autolabor.transform.Transformation
import cn.autolabor.util.reflect.TypeNode
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.modules.Coordination.Map
import org.mechdancer.modules.Coordination.Robot
import org.mechdancer.modules.Default
import org.mechdancer.modules.PathFollowerModule

fun main() {
    val handle = ServerManager.me().getOrCreateMessageHandle("cmdvel", TypeNode(Msg2DOdometry::class.java))
    val locate = ServerManager.me().getOrCreateMessageHandle("abs", TypeNode(Msg2DOdometry::class.java))
    ServerManager.me().dump()

    val remote = Default.remote
    val system = Default.system
    // 导航模块
    val follower = PathFollowerModule { v, w ->
        handle.pushSubData(Msg2DOdometry(Msg2DPose(), Msg2DTwist(v, .0, w)))
    }
    // PM1.initialize()
    // PM1.locked = false
    // PM1.setCommandEnabled(false)
    // 启动里程计资源
    launchBlocking(100L) {
        val temp = locate.firstData as? Msg2DOdometry ?: return@launchBlocking Unit
        val x = temp.pose.x
        val y = temp.pose.y
        val theta = temp.pose.yaw
        val (p, d) = follower.offset plusDelta Odometry(vector2DOf(x, y), theta.toRad())
        system.cleanup(Robot to Map, System.currentTimeMillis() - 5000)
        system[Robot to Map] = Transformation.fromPose(p, d)
        follower.record(p)
        remote.paint("odometry", p.x, p.y, d.asRadian())
    }
    // launch parser
    follower.parseRepeatedly()
}
