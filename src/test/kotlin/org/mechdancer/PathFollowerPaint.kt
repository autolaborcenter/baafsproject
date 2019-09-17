package org.mechdancer

import kotlinx.coroutines.runBlocking
import org.mechdancer.modules.devices.Chassis.FrameworkRemoteChassis
import org.mechdancer.modules.startPathFollower

fun main() = runBlocking {
    val chassis = FrameworkRemoteChassis(this)
    startPathFollower(
        robotOnMap = chassis.robotPose,
        twistCommand = chassis.twistCommand)
}
