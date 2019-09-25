package org.mechdancer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.modules.Obstacle
import org.mechdancer.modules.await
import org.mechdancer.modules.devices.Chassis.PM1Chassis
import org.mechdancer.modules.devices.Locator.FrameworkRemoteLocator.MarvelmindLocator
import org.mechdancer.modules.startLocationFilter
import org.mechdancer.modules.startPathFollower

fun main() {
    val scope = CoroutineScope(Dispatchers.Default)
    // 话题
    val robotOnMap = Channel<Stamped<Odometry>>(Channel.CONFLATED)
    // 模块
    val locator = MarvelmindLocator(scope)
    val chassis = PM1Chassis(scope)
    val obstacle = Obstacle(scope, chassis.twistCommand)
    // 任务
    scope.startLocationFilter(
        robotOnLocator = locator.robotLocation,
        robotOnOdometry = chassis.robotPose,
        robotOnMap = robotOnMap)
    scope.startPathFollower(
        robotOnMap = chassis.robotPose,
        twistCommand = obstacle.toObstacle)
    scope.await()
}

