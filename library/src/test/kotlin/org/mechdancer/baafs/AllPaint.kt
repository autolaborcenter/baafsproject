package org.mechdancer.baafs

import cn.autolabor.ChassisModuleBuilderDsl.Companion.startChassis
import cn.autolabor.locator.LocationFusionModuleBuilderDsl.Companion.startLocationFusion
import cn.autolabor.pathfollower.PathFollowerModuleBuilderDsl.Companion.startPathFollower
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.baafs.modules.LinkMode.Direct
import org.mechdancer.baafs.modules.startBeacon
import org.mechdancer.baafs.modules.startObstacleAvoiding
import org.mechdancer.channel
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.exceptions.ApplicationException

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
        runBlocking(Dispatchers.Default) {
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
            val parsing = startPathFollower(
                robotOnMap = robotOnMap,
                commandOut = commandToObstacle)
            startObstacleAvoiding(
                mode = mode,
                commandIn = commandToObstacle,
                commandOut = commandToRobot)
            parsing.start()
            coroutineContext[Job]
                ?.children
                ?.filter { it.isActive }
                ?.toList()
                ?.run { println("running coroutines: $size") }
        }
    } catch (e: ApplicationException) {
        System.err.println(e.message)
    } catch (e: Throwable) {
        System.err.println("program terminate because of ${e::class.simpleName}")
    }
}
