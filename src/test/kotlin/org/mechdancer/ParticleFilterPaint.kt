package org.mechdancer

import cn.autolabor.pm1.sdk.PM1
import org.mechdancer.dependency.must
import org.mechdancer.modules.LocatorModule
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.remote.resources.MulticastSockets

fun main() {
    // 网络节点
    val remote = remoteHub("particle filter test").also {
        it.openAllNetworks()
        println("remote launched on ${it.components.must<MulticastSockets>().address}")
    }
    // 定位模块
    val locator = LocatorModule(remote) { (p, d) ->
        remote.paint("filter", p.x, p.y, d.value)
    }
    // launch tasks
    with(locator) {
        // launch pm1
        PM1.locked = false
        PM1.setCommandEnabled(false)
        // launch marvelmind
        marvelmindBlockTask()
    }
}
