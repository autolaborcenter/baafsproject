package org.mechdancer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.mechdancer.modules.await
import org.mechdancer.modules.devices.Chassis.PM1Chassis
import org.mechdancer.modules.obstacleDetecting
import org.mechdancer.modules.startPathFollower

fun main() {
    obstacleDetecting()
    val scope = CoroutineScope(Dispatchers.Default)
    val chassis = PM1Chassis(scope)
    scope.startPathFollower(
        robotOnMap = chassis.robotPose,
        twistCommand = chassis.twistCommand)
    scope.await()
}
