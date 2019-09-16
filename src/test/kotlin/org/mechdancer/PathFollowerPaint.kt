package org.mechdancer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import org.mechdancer.modules.PathFollowerModule
import org.mechdancer.modules.devices.Chassis.FrameworkRemoteChassis

fun main() {
    FrameworkRemoteChassis(CoroutineScope(Dispatchers.Default))
        .use { chassis ->
            PathFollowerModule(
                GlobalScope,
                robotOnMap = chassis.robotPose,
                twistChannel = chassis.twistCommand
            ).use { it.parseRepeatedly() }
        }
}

