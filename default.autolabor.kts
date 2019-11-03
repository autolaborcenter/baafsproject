package cn.autolabor.baafs

import cn.autolabor.ChassisModuleBuilderDsl.Companion.startChassis
import cn.autolabor.business.BusinessBuilderDsl.Companion.business
import cn.autolabor.business.parseFromConsole
import cn.autolabor.business.registerBusinessParser
import cn.autolabor.core.server.DefaultSetup
import cn.autolabor.core.server.ServerManager
import cn.autolabor.locator.LocationFusionModuleBuilderDsl.Companion.startLocationFusion
import cn.autolabor.module.networkhub.UDPMulticastBroadcaster
import cn.autolabor.pathfollower.Proportion
import com.marvelmind.MobileBeaconModuleBuilderDsl.Companion.startMobileBeacon
import org.mechdancer.YChannel
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.channel
import org.mechdancer.common.Odometry
import org.mechdancer.common.Odometry.Companion
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.console.parser.buildParser
import org.mechdancer.exceptions.ApplicationException
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.exceptions.ExceptionServer
import org.mechdancer.geometry.angle.toDegree
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.networksInfo
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.simulation.map.shape.Circle
import kotlin.system.exitProcess

ServerManager.setSetup(object : DefaultSetup() {
    override fun start() {
        ServerManager.me().register(UDPMulticastBroadcaster())
    }
})

val remote by lazy {
    remoteHub("painter")
        .apply {
            openAllNetworks()
            println(networksInfo())
        }
}

// 话题
val robotOnOdometry = YChannel<Stamped<Odometry>>()
val robotOnMap = channel<Stamped<Odometry>>()
val beaconOnMap = channel<Stamped<Vector2D>>()
val exceptions = channel<ExceptionMessage>()
val commandToObstacle = channel<NonOmnidirectional>()
val commandToSwitch = channel<NonOmnidirectional>()
val commandToRobot = channel<NonOmnidirectional>()
// 任务
try {
    runBlocking(Dispatchers.Default) {
        println("trying to connect to pm1 chassis...")
        startChassis(
            odometry = robotOnOdometry.input,
            command = commandToRobot
        ) {
            port = null
            period = 40L
            controlTimeout = 400L
        }
        println("done")

        println("trying to connect to marvelmind mobile beacon...")
        startMobileBeacon(
            beaconOnMap = beaconOnMap,
            exceptions = exceptions
        ) {
            port = "/dev/beacon"
            retryInterval = 100L
            connectionTimeout = 3000L
            parseTimeout = 2500L
            dataTimeout = 2000L

            delayLimit = 400L

            val height = -1.6
            val radius = .3
            heightRange = height - radius..height + radius
        }
        println("done")

        println("trying to connect to faselase lidars...")
        startObstacleAvoiding(
            launchLidar = true,
            commandIn = commandToObstacle,
            commandOut = commandToSwitch)
        println("done")

        println("staring data process modules...")
        val exceptionServer = ExceptionServer()
        val filter = startLocationFusion(
            robotOnOdometry = robotOnOdometry.outputs[0],
            beaconOnMap = beaconOnMap,
            robotOnMap = robotOnMap
        ) {
            filter {
                beaconOnRobot = vector2DOf(-.01, -.02)
                maxInconsistency = .1
                convergence { (age, _, d) -> age > .2 && d > .9 }
                divergence { (age, _, _) -> age < .1 }
            }
            painter = remote
        }
        val business = business(
            robotOnMap = robotOnMap,
            robotOnOdometry = robotOnOdometry.outputs[1],
            commandOut = commandToObstacle,
            exceptions = exceptions
        ) {
            pathInterval = .05
            localRadius = .5
            directionLimit = (-120).toDegree()
            follower {
                sensorPose = Odometry.pose(x = .2)
                lightRange = Circle(.24, 16)
                controller = Proportion(1.0)
                minTipAngle = 60.toDegree()
                minTurnAngle = 15.toDegree()
                maxLinearSpeed = .1
                maxAngularSpeed = .3.toRad()
            }
            painter = remote
        }
        launch {
            for (e in exceptions)
                exceptionServer.update(e)
        }
        launch {
            for (command in commandToSwitch)
                if (exceptionServer.isEmpty())
                    commandToRobot.send(command)
            commandToRobot.close()
        }
        println("done")
//            launch {
//                val topic = "fusion".handler<Msg2DOdometry>()
//                for ((_, pose) in robotOnMap.outputs[1])
//                    topic.pushSubData(Msg2DOdometry(Msg2DPose(pose.p.x, pose.p.y, pose.d.asRadian()), null))
//            }
        launch {
            val parser = buildParser {
                this["coroutines"] = { coroutineContext[Job]?.children?.count() }
                this["exceptions"] = { exceptionServer.get().joinToString("\n") }
                this["fusion state"] = {
                    buildString {
                        val now = System.currentTimeMillis()
                        appendln(filter.lastQuery
                                     ?.let { (t, pose) -> "last locate at $pose ${now - t}ms ago" }
                                 ?: "never query pose before")
                        val (t, quality) = filter.quality
                        appendln("particles last update ${now - t}ms ago")
                        appendln("now system is ${if (filter.isConvergent) "" else "not"} ready for work")
                        append("quality = $quality")
                    }
                }
                registerBusinessParser(business, this)
            }
            while (isActive) parser.parseFromConsole()
        }
    }
} catch (e: CancellationException) {
} catch (e: ApplicationException) {
    System.err.println(e.message)
} catch (e: Throwable) {
    System.err.println("program terminate because of ${e::class.simpleName}")
    e.printStackTrace()
} finally {
    Thread.sleep(400L)
    println("program stopped")
}
exitProcess(0)
