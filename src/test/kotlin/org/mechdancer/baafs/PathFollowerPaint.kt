package org.mechdancer.baafs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.mechdancer.baafs.modules.*
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.modules.*

@ExperimentalCoroutinesApi
fun main() {
    val mode = LinkMode.Framework
    // 话题
    val robotOnMap = channel<Stamped<Odometry>>()
    val commandToObstacle = channel<NonOmnidirectional>()
    val commandToRobot = channel<NonOmnidirectional>()
    // 任务
    with(CoroutineScope(Dispatchers.Default)) {
        startChassis(
            mode = mode,
            odometry = robotOnMap,
            command = commandToRobot)
        startPathFollower(
            robotOnMap = robotOnMap,
            commandOut = commandToObstacle,
            remote = null)
        startObstacleAvoiding(
            mode = mode,
            commandIn = commandToObstacle,
            commandOut = commandToRobot)
        await()
    }
}
