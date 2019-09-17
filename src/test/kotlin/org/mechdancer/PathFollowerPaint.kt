package org.mechdancer

import cn.autolabor.FilterTwistTask
import cn.autolabor.ObstacleDetectionTask
import cn.autolabor.PoseDetectionTask
import cn.autolabor.core.server.ServerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.mechdancer.modules.await
import org.mechdancer.modules.devices.Chassis.FrameworkRemoteChassis
import org.mechdancer.modules.startPathFollower

fun main() {
    ServerManager.me().loadConfig("conf/obstacle.conf")
    ServerManager.me().register(ObstacleDetectionTask("ObstacleDetectionTask"))
    ServerManager.me().register(PoseDetectionTask("PoseDetectionTask"))
    ServerManager.me().register(FilterTwistTask("FilterTwistTask"))
    ServerManager.me().dump()
    val scope = CoroutineScope(Dispatchers.Default)
    val chassis = FrameworkRemoteChassis(scope)
    scope.startPathFollower(
        robotOnMap = chassis.robotPose,
        twistCommand = chassis.twistCommand)
    scope.await()
}
