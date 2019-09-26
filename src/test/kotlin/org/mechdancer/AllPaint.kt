package org.mechdancer

import cn.autolabor.locator.ParticleFilterBuilder.Companion.particleFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.modules.*
import org.mechdancer.modules.devices.Chassis.PM1Chassis
import org.mechdancer.modules.devices.Locator.FrameworkRemoteLocator.MarvelmindLocator

@ExperimentalCoroutinesApi
fun main() {
    // 话题
    val robotOnMap = Channel<Stamped<Odometry>>(Channel.CONFLATED)
    val commandToObstacle = Channel<NonOmnidirectional>(Channel.CONFLATED)
    // 启动协程
    val scope = CoroutineScope(Dispatchers.Default)
    // 模块
    val locator = MarvelmindLocator(scope)
    val chassis = PM1Chassis(scope)
    // 任务
    scope.startLocationFilter(
        robotOnLocator = locator.robotLocation,
        robotOnOdometry = chassis.robotPose,
        robotOnMap = robotOnMap,
        filter = particleFilter {
            locatorOnRobot = vector2DOf(-0.3, .0)
        }.apply {
            registerLogger()
            registerPainter()
        })
    scope.startPathFollower(
        robotOnMap = robotOnMap,
        commandOut = commandToObstacle)
    scope.startObstacleAvoiding(
        commandIn = commandToObstacle,
        commandOut = chassis.twistCommand)
    scope.await()
}
