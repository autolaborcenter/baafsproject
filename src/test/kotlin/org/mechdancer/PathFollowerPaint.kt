package org.mechdancer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.mechdancer.modules.Obstacle
import org.mechdancer.modules.await
import org.mechdancer.modules.devices.Chassis.PM1Chassis
import org.mechdancer.modules.startPathFollower

fun main() {
    val scope = CoroutineScope(Dispatchers.Default)
    val chassis = PM1Chassis(scope)
    val obstacle = Obstacle(scope, chassis.twistCommand)
    scope.startPathFollower(
        robotOnMap = chassis.robotPose,
        twistCommand = obstacle.toObstacle)
    scope.await()
}
