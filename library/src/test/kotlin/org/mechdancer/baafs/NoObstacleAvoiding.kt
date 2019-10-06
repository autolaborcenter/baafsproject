package org.mechdancer.baafs

import cn.autolabor.ChassisModuleBuilderDsl.Companion.startChassis
import cn.autolabor.locator.LocationFusionModuleBuilderDsl.Companion.startLocationFusion
import cn.autolabor.pathfollower.PathFollowerModuleBuilderDsl.Companion.startPathFollower
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.baafs.modules.LinkMode.Direct
import org.mechdancer.baafs.modules.startBeacon
import org.mechdancer.channel
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity.NonOmnidirectional

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
    try {
        runBlocking {
            startChassis(
                odometry = robotOnOdometry,
                command = commandToRobot)
            startBeacon(
                mode = mode,
                beaconOnMap = beaconOnMap)
            startLocationFusion(
                robotOnOdometry = robotOnOdometry,
                beaconOnMap = beaconOnMap,
                robotOnMap = robotOnMap) {
                filter {
                    beaconOnRobot = vector2DOf(-0.37, 0)
                }
            }
            startPathFollower(
                robotOnMap = robotOnMap,
                commandOut = commandToObstacle)
            coroutineContext[Job]
                ?.children
                ?.filter { it.isActive }
                ?.toList()
                ?.run { println("running coroutines: $size") }
        }
    } catch (e: Exception) {
        System.err.println("program stop with exception: ${e.message}")
    }
}
