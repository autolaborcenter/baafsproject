package org.mechdancer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.modules.await
import org.mechdancer.modules.devices.Chassis.PM1Chassis
import org.mechdancer.modules.startPathFollower

@ExperimentalCoroutinesApi
fun main() {
    // 话题
    val commandToObstacle = Channel<NonOmnidirectional>(Channel.CONFLATED)
    // 启动协程
    val scope = CoroutineScope(Dispatchers.Default)
    // 模块
    val chassis = PM1Chassis(scope)
    // 任务
    scope.startPathFollower(
        robotOnMap = chassis.robotPose,
        commandOut = chassis.twistCommand)
//    scope.startObstacleAvoiding(
//        commandIn = commandToObstacle,
//        commandOut = chassis.twistCommand)
    scope.await()
}
