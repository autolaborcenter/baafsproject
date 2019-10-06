package org.mechdancer.baafs.modules

import cn.autolabor.ChassisModuleBuilderDsl.Companion.startChassis
import cn.autolabor.core.server.DefaultSetup
import cn.autolabor.core.server.ServerManager
import cn.autolabor.locator.LocationFusionModuleBuilderDsl.Companion.startLocationFusion
import cn.autolabor.pathfollower.PathFollowerModuleBuilderDsl.Companion.startPathFollower
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.baafs.modules.LinkMode.Direct
import org.mechdancer.channel
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.dependency.must
import org.mechdancer.exceptions.ApplicationException
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.remote.resources.MulticastSockets
import org.mechdancer.remote.resources.Networks
import kotlin.concurrent.thread

ServerManager.setSetup(object : DefaultSetup() {
    override fun start() = Unit
})

val remote = remoteHub("painter")
    .apply {
        openAllNetworks()
        println("simulator open ${components.must<Networks>().view.size} networks on ${components.must<MulticastSockets>().address}")
        thread(isDaemon = true) { while (true) invoke() }
    }

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
        println("trying to connect to pm1 chassis...")
        startChassis(
            odometry = robotOnOdometry,
            command = commandToRobot)
        println("done")
        println("trying to connect to marvelmind mobile beacon...")
        startBeacon(
            mode = mode,
            beaconOnMap = beaconOnMap)
        println("done")
        startLocationFusion(
            robotOnOdometry = robotOnOdometry,
            beaconOnMap = beaconOnMap,
            robotOnMap = robotOnMap) {
            filter {
                beaconOnRobot = vector2DOf(-0.037, 0)
            }
            painter = remote
        }
        val parsing = startPathFollower(
            robotOnMap = robotOnMap,
            commandOut = commandToObstacle)
        println("trying to connect to faselase lidars...")
        startObstacleAvoiding(
            mode = mode,
            commandIn = commandToObstacle,
            commandOut = commandToRobot)
        println("done")
        coroutineContext[Job]
            ?.children
            ?.filter { it.isActive }
            ?.toList()
            ?.run { println("running coroutines: $size") }
        parsing.start()
    }
} catch (e: ApplicationException) {
    System.err.println(e.message)
} catch (e: Throwable) {
    System.err.println("program terminate because of ${e::class.simpleName}")
    e.printStackTrace()
} finally {
    println("program stopped")
}
