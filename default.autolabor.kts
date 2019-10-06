package org.mechdancer.baafs

import cn.autolabor.locator.LocationFusionModuleBuilderDsl.Companion.startLocationFusion
import cn.autolabor.pathfollower.PathFollowerModuleBuilderDsl.Companion.startPathFollower
import kotlinx.coroutines.*
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.baafs.modules.LinkMode.Direct
import org.mechdancer.baafs.modules.startBeacon
import org.mechdancer.baafs.modules.startChassis
import org.mechdancer.baafs.modules.startObstacleAvoiding
import org.mechdancer.channel
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.exceptions.ApplicationException

ServerManager.setSetup(object : DefaultSetup() {
    override fun start() = Unit
})

val mode = Direct
// 话题
val robotOnOdometry = channel<Stamped<Odometry>>()
val robotOnMap = channel<Stamped<Odometry>>()
val beaconOnMap = channel<Stamped<Vector2D>>()
val commandToObstacle = channel<NonOmnidirectional>()
val commandToRobot = channel<NonOmnidirectional>()
// 任务
try {
    with(CoroutineScope(Dispatchers.Default)) {
        startChassis(
            mode = mode,
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
        coroutineContext[Job]
            ?.children
            ?.filter { it.isActive }
            ?.toList()
            ?.run { kotlin.io.println("running coroutines: $size") }
        runBlocking { parsing.join() }
    }
} catch (e: ApplicationException) {
    System.err.println(e.message)
} catch (e: Throwable) {
    System.err.println("program terminate because of ${e::class.simpleName}")
}
