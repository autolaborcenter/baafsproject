package org.mechdancer

import cn.autolabor.Odometry
import cn.autolabor.Stamped
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.mechdancer.modules.devices.Chassis.FrameworkRemoteChassis
import org.mechdancer.modules.devices.Locator.FrameworkRemoteLocator
import org.mechdancer.modules.startLocationFilter

fun main() = runBlocking {
    // 话题
    val locator = FrameworkRemoteLocator(this)
    val chassis = FrameworkRemoteChassis(this)
    val robotOnMap = Channel<Stamped<Odometry>>(Channel.CONFLATED)
    startLocationFilter(
        robotOnLocator = locator.robotLocation,
        robotOnOdometry = chassis.robotPose,
        robotOnMap = robotOnMap)
}
