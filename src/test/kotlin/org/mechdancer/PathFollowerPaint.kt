package org.mechdancer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.mechdancer.modules.await
import org.mechdancer.modules.devices.Chassis.FrameworkRemoteChassis
import org.mechdancer.modules.startPathFollower

fun main() {
    val scope = CoroutineScope(Dispatchers.Default)
    val chassis = FrameworkRemoteChassis(scope)
    scope.startPathFollower(
        robotOnMap = chassis.robotPose,
        twistCommand = chassis.twistCommand)
    scope.await()
}
