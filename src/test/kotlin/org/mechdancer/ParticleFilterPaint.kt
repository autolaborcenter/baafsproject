package org.mechdancer

import cn.autolabor.Odometry
import cn.autolabor.Stamped
import cn.autolabor.core.server.ServerManager
import cn.autolabor.message.navigation.Msg2DOdometry
import cn.autolabor.message.navigation.Msg2DPose
import cn.autolabor.message.navigation.Msg2DTwist
import cn.autolabor.util.reflect.TypeNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.mechdancer.modules.await
import org.mechdancer.modules.devices.Chassis.FrameworkRemoteChassis
import org.mechdancer.modules.devices.Locator.FrameworkRemoteLocator
import org.mechdancer.modules.startLocationFilter

fun main() {
    val scope = CoroutineScope(Dispatchers.Default)
    // 话题
    val locator = FrameworkRemoteLocator(scope)
    val chassis = FrameworkRemoteChassis(scope)
    val robotOnMap = Channel<Stamped<Odometry>>(Channel.CONFLATED)
    scope.startLocationFilter(
        robotOnLocator = locator.robotLocation,
        robotOnOdometry = chassis.robotPose,
        robotOnMap = robotOnMap)
    scope.launch {
        val topic = ServerManager.me().getOrCreateMessageHandle("fusion", TypeNode(Msg2DOdometry::class.java))
        for ((_, data) in robotOnMap) {
            val (p, d) = data
            topic.pushSubData(Msg2DOdometry(Msg2DPose(p.x, p.y, d.asRadian()), Msg2DTwist()))
        }
    }
    scope.await()
}
