package org.mechdancer

import cn.autolabor.pm1.sdk.PM1
import cn.autolabor.transform.TransformSystem
import cn.autolabor.transform.Transformation
import org.mechdancer.dependency.must
import org.mechdancer.modules.Coordination
import org.mechdancer.modules.Coordination.BaseLink
import org.mechdancer.modules.Coordination.Map
import org.mechdancer.modules.LocatorModule
import org.mechdancer.modules.PathFollowerModule
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.remote.resources.MulticastSockets
import kotlin.concurrent.thread

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
    // 定位模块
    val locator = LocatorModule(remote) { (p, d) ->
        system.cleanup(BaseLink to Map)
        system[BaseLink to Map] = Transformation.fromPose(p, d)
        follower.recordNode(p)
    }
    // launch tasks
    with(locator) {
        // launch pm1
        PM1.locked = false
        PM1.setCommandEnabled(false)
        thread { pm1BlockTask() }
        // launch marvelmind
        thread { marvelmindBlockTask() }
    }
    // launch parser
    follower.parseRepeatedly()
}
