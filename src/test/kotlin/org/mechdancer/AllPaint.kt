package org.mechdancer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.modules.await
import org.mechdancer.modules.devices.Chassis.FrameworkRemoteChassis
import org.mechdancer.modules.devices.Locator.FrameworkRemoteLocator
import org.mechdancer.modules.obstacleDetecting
import org.mechdancer.modules.startLocationFilter
import org.mechdancer.modules.startPathFollower

fun main() {
    obstacleDetecting()
    val scope = CoroutineScope(Dispatchers.Default)
    // 话题
    val robotOnMap = Channel<Stamped<Odometry>>(Channel.CONFLATED)
    // 模块
    val locator = FrameworkRemoteLocator(scope)
    val chassis = FrameworkRemoteChassis(scope)
    scope.startLocationFilter(
        robotOnLocator = locator.robotLocation,
        robotOnOdometry = chassis.robotPose,
        robotOnMap = robotOnMap)
    scope.startPathFollower(
        robotOnMap = robotOnMap,
        twistCommand = chassis.twistCommand)
    scope.await()
}

