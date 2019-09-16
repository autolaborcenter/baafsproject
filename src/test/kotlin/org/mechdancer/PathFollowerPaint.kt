package org.mechdancer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.mechdancer.modules.PathFollowerModule
import org.mechdancer.modules.await
import org.mechdancer.modules.devices.Chassis.FrameworkRemoteChassis

fun main() {
    val scope = CoroutineScope(Dispatchers.Default)
    val chassis = FrameworkRemoteChassis(scope)
    PathFollowerModule(
        scope,
        robotOnMap = chassis.robotPose,
        twistChannel = chassis.twistCommand
    ).parseRepeatedly()
    scope.await()
    println("xxx")
}

