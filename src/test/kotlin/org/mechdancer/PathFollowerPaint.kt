package org.mechdancer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.modules.*
import org.mechdancer.modules.LinkMode.Direct

@ExperimentalCoroutinesApi
fun main() {
    val mode = Direct
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
            commandOut = commandToObstacle)
        startObstacleAvoiding(
            mode = mode,
            commandIn = commandToObstacle,
            commandOut = commandToRobot)
        await()
    }
}
