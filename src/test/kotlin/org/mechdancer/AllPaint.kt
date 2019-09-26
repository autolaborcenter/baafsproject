package org.mechdancer

import cn.autolabor.locator.ParticleFilterBuilder.Companion.particleFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.modules.*
import org.mechdancer.modules.LinkMode.Direct

@ExperimentalCoroutinesApi
fun main() {
    // 话题
    val robotOnOdometry = channel<Stamped<Odometry>>()
    val robotOnMap = channel<Stamped<Odometry>>()
    val beaconOnMap = channel<Stamped<Vector2D>>()
    val commandToObstacle = channel<NonOmnidirectional>()
    val commandToRobot = channel<NonOmnidirectional>()
    // 任务
    with(CoroutineScope(Dispatchers.Default)) {
        startChassis(
            mode = Direct,
            odometry = robotOnOdometry,
            command = commandToRobot)
        startLocateSensor(
            mode = Direct,
            beaconOnMap = beaconOnMap)
        startLocationFilter(
            robotOnOdometry = robotOnOdometry,
            beaconOnMap = beaconOnMap,
            robotOnMap = robotOnMap,
            filter = particleFilter {
                locatorOnRobot = vector2DOf(-0.3, .0)
            }.apply {
                registerLogger()
                registerPainter()
            })
        startPathFollower(
            robotOnMap = robotOnMap,
            commandOut = commandToObstacle)
        startObstacleAvoiding(
            commandIn = commandToObstacle,
            commandOut = commandToRobot)
        await()
    }
}
