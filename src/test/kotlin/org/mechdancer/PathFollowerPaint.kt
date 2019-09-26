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
    // 话题
    val robotOnMap = channel<Stamped<Odometry>>()
    val commandToObstacle = channel<NonOmnidirectional>()
    val commandToRobot = channel<NonOmnidirectional>()
    // 任务
    with(CoroutineScope(Dispatchers.Default)) {
        startChassis(
            mode = Direct,
            odometry = robotOnMap,
            command = commandToRobot)
        startPathFollower(
            robotOnMap = robotOnMap,
            commandOut = commandToObstacle)
        startObstacleAvoiding(
            commandIn = commandToObstacle,
            commandOut = commandToRobot)
        await()
    }
}
