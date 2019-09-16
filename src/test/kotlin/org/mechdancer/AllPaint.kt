package org.mechdancer

import cn.autolabor.Odometry
import cn.autolabor.Stamped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import org.mechdancer.modules.PathFollowerModule
import org.mechdancer.modules.devices.Chassis.FrameworkRemoteChassis
import org.mechdancer.modules.devices.Locator.FrameworkRemoteLocator
import org.mechdancer.modules.startLocationFilter

fun main() {
    // 话题
    val robotOnMap = Channel<Stamped<Odometry>>(Channel.CONFLATED)
    // 模块
    val scope = CoroutineScope(Dispatchers.Default)
    val locator = FrameworkRemoteLocator(scope)
    val chassis = FrameworkRemoteChassis(scope)
    scope.startLocationFilter(
        robotOnLocator = locator.robotLocation,
        robotOnOdometry = chassis.robotPose,
        robotOnMap = robotOnMap)
    // 导航模块
    PathFollowerModule(scope, chassis.robotPose, chassis.twistCommand)
        .use { it.parseRepeatedly() }
}

