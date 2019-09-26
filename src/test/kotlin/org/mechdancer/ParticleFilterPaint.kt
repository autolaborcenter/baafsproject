package org.mechdancer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.modules.await
import org.mechdancer.modules.devices.Chassis.FrameworkRemoteChassis
import org.mechdancer.modules.devices.Locator.FrameworkRemoteLocator
import org.mechdancer.modules.startLocationFilter

fun main() {
    // 话题
    val robotOnMap = Channel<Stamped<Odometry>>(Channel.CONFLATED)
    // 启动协程
    val scope = CoroutineScope(Dispatchers.Default)
    // 模块
    val locator = FrameworkRemoteLocator(scope)
    val chassis = FrameworkRemoteChassis(scope)
    // 启动任务
    scope.startLocationFilter(
        robotOnLocator = locator.robotLocation,
        robotOnOdometry = chassis.robotPose,
        robotOnMap = robotOnMap)
//    scope.launch {
//        val topic = ServerManager.me().getOrCreateMessageHandle("fusion", TypeNode(Msg2DOdometry::class.java))
//        for ((_, data) in robotOnMap) {
//            val (p, d) = data
//            topic.pushSubData(Msg2DOdometry(Msg2DPose(p.x, p.y, d.asRadian()), Msg2DTwist()))
//        }
//    }
    scope.await()
}
