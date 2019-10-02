package org.mechdancer.baafs

import cn.autolabor.pathfollower.PathFollowerModule.Companion.startPathFollower
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.mechdancer.await
import org.mechdancer.baafs.modules.LinkMode
import org.mechdancer.baafs.modules.startChassis
import org.mechdancer.baafs.modules.startObstacleAvoiding
import org.mechdancer.channel
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity.NonOmnidirectional

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
