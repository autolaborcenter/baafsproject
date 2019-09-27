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
    val mode = Direct
    // 话题
    val robotOnOdometry = channel<Stamped<Odometry>>()
    val robotOnMap = channel<Stamped<Odometry>>()
    val beaconOnMap = channel<Stamped<Vector2D>>()
    val commandToObstacle = channel<NonOmnidirectional>()
    val commandToRobot = channel<NonOmnidirectional>()
    // 任务
    with(CoroutineScope(Dispatchers.Default)) {
        startChassis(
            mode = mode,
            odometry = robotOnOdometry,
            command = commandToRobot)
        startBeacon(
            mode = mode,
            beaconOnMap = beaconOnMap)
        startLocationFilter(
            robotOnOdometry = robotOnOdometry,
            beaconOnMap = beaconOnMap,
            robotOnMap = robotOnMap,
            filter = particleFilter {
                beaconOnRobot = vector2DOf(-0.3, .0)
            }.apply {
                registerLogger()
                registerPainter()
            })
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
