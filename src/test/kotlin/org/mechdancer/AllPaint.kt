package org.mechdancer

import cn.autolabor.Odometry
import cn.autolabor.Stamped
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.mechdancer.modules.devices.Chassis.FrameworkRemoteChassis
import org.mechdancer.modules.devices.Locator.FrameworkRemoteLocator
import org.mechdancer.modules.startLocationFilter
import org.mechdancer.modules.startPathFollower

fun main() = runBlocking {
    // 话题
    val robotOnMap = Channel<Stamped<Odometry>>(Channel.CONFLATED)
    // 模块
    val locator = FrameworkRemoteLocator(this)
    val chassis = FrameworkRemoteChassis(this)
    startLocationFilter(
        robotOnLocator = locator.robotLocation,
        robotOnOdometry = chassis.robotPose,
        robotOnMap = robotOnMap)
    startPathFollower(
        robotOnMap = robotOnMap,
        twistCommand = chassis.twistCommand)
}

