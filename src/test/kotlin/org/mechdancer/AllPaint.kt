package org.mechdancer

import cn.autolabor.pm1.sdk.PM1
import cn.autolabor.transform.Transformation
import org.mechdancer.modules.Coordination.Map
import org.mechdancer.modules.Coordination.Robot
import org.mechdancer.modules.Default
import org.mechdancer.modules.LocatorModule
import org.mechdancer.modules.PathFollowerModule
import kotlin.concurrent.thread

fun main() {
    // 坐标系管理器
    val system = Default.system
    // 导航模块
    val follower = PathFollowerModule()
    // 定位模块
    val locator = LocatorModule { (time, data) ->
        system.cleanup(Robot to Map, System.currentTimeMillis() - 5000)
        system[Robot to Map, time] = Transformation.fromPose(data.p, data.d)
        follower.record(data.p)
    }
    // launch tasks
    thread(name = "marvelmind") {
        // launch pm1
        PM1.initialize()
        PM1.locked = false
        PM1.setCommandEnabled(false)
        // launch marvelmind
        locator.marvelmindBlockTask()
    }
    // launch parser
    follower.parseRepeatedly()
}
