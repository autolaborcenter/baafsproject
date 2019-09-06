package org.mechdancer

import cn.autolabor.pm1.Resource
import cn.autolabor.pm1.sdk.PM1
import cn.autolabor.transform.TransformSystem
import cn.autolabor.transform.Transformation
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.dependency.must
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.modules.Coordination
import org.mechdancer.modules.Coordination.BaseLink
import org.mechdancer.modules.Coordination.Map
import org.mechdancer.modules.PathFollowerModule
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.remote.resources.MulticastSockets

fun main() {
    // 网络节点
    val remote = remoteHub("path follower test").also {
        it.openAllNetworks()
        println("remote launched on ${it.components.must<MulticastSockets>().address}")
    }
    // 坐标系管理器
    val system = TransformSystem<Coordination>()
    // 导航模块
    val follower = PathFollowerModule(remote, system)
    // 启动里程计资源
    Resource { odometry ->
        val p = vector2DOf(odometry.x, odometry.y)
        val d = odometry.theta.toRad()
        system.cleanup(BaseLink to Map)
        system[BaseLink to Map] = Transformation.fromPose(p, d)
        follower.recordNode(p)
        remote.paint("odometry", p.x, p.y, d.asRadian())
    }.use { pm1 ->
        // launch pm1
        PM1.locked = false
        PM1.setCommandEnabled(false)
        launchBlocking { pm1() }
        // launch parser
        follower.parseRepeatedly()
    }
}
