package org.mechdancer.baafs.modules

import cn.autolabor.ChassisModuleBuilderDsl.Companion.startChassis
import cn.autolabor.core.server.DefaultSetup
import cn.autolabor.core.server.ServerManager
import cn.autolabor.locator.LocationFusionModuleBuilderDsl.Companion.startLocationFusion
import cn.autolabor.pathfollower.PathFollowerModuleBuilderDsl.Companion.startPathFollower
import com.marvelmind.MobileBeaconModuleBuilderDsl.Companion.startMobileBeacon
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
import org.mechdancer.exceptions.ApplicationException
import org.mechdancer.networksInfo
import org.mechdancer.remote.presets.remoteHub
import kotlin.concurrent.thread

ServerManager.setSetup(object : DefaultSetup() {
    override fun start() = Unit
})

val remote = remoteHub("painter")
    .apply {
        openAllNetworks()
        println(networksInfo())
        thread(isDaemon = true) { while (true) invoke() }
    }

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
            command = commandToRobot
        ) {
            port = null
            period = 30L
            controlTimeout = 300L
        }
        println("done")

        println("trying to connect to marvelmind mobile beacon...")
        startMobileBeacon(
            beaconOnMap = beaconOnMap
        ) {
            port = null
            openTimeout = 1000L
            dataTimeout = 2000L
        }
        println("done")

        println("trying to connect to faselase lidars...")
        startObstacleAvoiding(
            mode = Direct,
            commandIn = commandToObstacle,
            commandOut = commandToRobot)
        println("done")

        startLocationFusion(
            robotOnOdometry = robotOnOdometry,
            beaconOnMap = beaconOnMap,
            robotOnMap = robotOnMap
        ) {
            filter {
                beaconOnRobot = vector2DOf(-.037, 0)
                beaconWeight = .15 * count
            }
            painter = remote
        }
        val parsing = startPathFollower(
            robotOnMap = robotOnMap,
            commandOut = commandToObstacle)

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
} finally {
    println("program stopped")
}
