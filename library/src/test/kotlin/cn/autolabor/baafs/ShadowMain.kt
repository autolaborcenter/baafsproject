package cn.autolabor.baafs

import cn.autolabor.ChassisModuleBuilderDsl.Companion.startChassis
import cn.autolabor.baafs.CollisionPredictingModuleBuilderDsl.Companion.startCollisionPredictingModule
import cn.autolabor.business.BusinessBuilderDsl.Companion.business
import cn.autolabor.business.parseFromConsole
import cn.autolabor.business.registerBusinessParser
import cn.autolabor.core.server.DefaultSetup
import cn.autolabor.core.server.ServerManager
import cn.autolabor.locator.LocationFusionModuleBuilderDsl.Companion.startLocationFusion
import cn.autolabor.module.networkhub.UDPMulticastBroadcaster
import cn.autolabor.pathfollower.Proportion
import com.faselase.FaselaseLidarSetBuilderDsl.Companion.faselaseLidarSet
import com.marvelmind.MobileBeaconModuleBuilderDsl.Companion.startMobileBeacon
import kotlinx.coroutines.*
import org.mechdancer.YChannel
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.channel
import org.mechdancer.common.Odometry
import org.mechdancer.common.Odometry.Companion.odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.console.parser.buildParser
import org.mechdancer.exceptions.ApplicationException
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.exceptions.ExceptionServer
import org.mechdancer.geometry.angle.toDegree
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.networksInfo
import org.mechdancer.remote.presets.RemoteHub
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.shape.Circle
import org.mechdancer.shape.Shape
import kotlin.math.PI
import kotlin.system.exitProcess

@ExperimentalCoroutinesApi
fun main() {
    ServerManager.setSetup(object : DefaultSetup() {
        override fun start() {
            ServerManager.me().register(UDPMulticastBroadcaster())
        }
    })

    val remote: RemoteHub? =
        remoteHub("painter")
            .apply {
                openAllNetworks()
                println(networksInfo())
            }

    // 话题
    val robotOnOdometry = YChannel<Stamped<Odometry>>()
    val robotOnMap = channel<Stamped<Odometry>>()
    val beaconOnMap = channel<Stamped<Vector2D>>()
    val exceptions = channel<ExceptionMessage>()
    val commandToSwitch = YChannel<NonOmnidirectional>()
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
                period = 30L
                controlTimeout = 300L
            }
            println("done")

            println("trying to connect to marvelmind mobile beacon...")
            startMobileBeacon(
                beaconOnMap = beaconOnMap,
                exceptions = exceptions
            ) {
                port = null
                retryInterval = 100L
                connectionTimeout = 3000L
                parseTimeout = 2500L
                dataTimeout = 2000L
                delayLimit = 400L
            }
            println("done")

            println("trying to connect to faselase lidars...")
            val robotOutline = listOf(
                vector2DOf(+.25, +.08),
                vector2DOf(+.10, +.20),
                vector2DOf(+.10, +.28),
                vector2DOf(-.10, +.28),
                vector2DOf(-.10, +.23),
                vector2DOf(-.25, +.23),
                vector2DOf(-.47, +.20),
                vector2DOf(-.47, -.20),
                vector2DOf(-.25, -.23),
                vector2DOf(-.10, -.23),
                vector2DOf(-.10, -.28),
                vector2DOf(+.10, -.28),
                vector2DOf(+.10, -.20),
                vector2DOf(+.25, -.08)
            ).let(::Shape)
            val lidarSet = faselaseLidarSet(exceptions = channel()) {
                launchTimeout = 5000L
                connectionTimeout = 800L
                dataTimeout = 400L
                retryInterval = 100L
                lidar(port = "/dev/pos3") {
                    tag = "FrontLidar"
                    pose = odometry(.113, 0, PI / 2)
                    inverse = false
                }
                lidar(port = "/dev/pos4") {
                    tag = "BackLidar"
                    pose = odometry(-.138, 0, PI / 2)
                    inverse = false
                }
                filter { p ->
                    p !in robotOutline
                }
            }
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
                commandOut = commandToSwitch.input,
                exceptions = exceptions
            ) {
                pathInterval = .05
                localRadius = .5
                directionLimit = (-120).toDegree()
                follower {
                    sensorPose = odometry(.2, .0)
                    lightRange = Circle(.24, 16)
                    controller = Proportion(1.0)
                    minTipAngle = 60.toDegree()
                    minTurnAngle = 15.toDegree()
                    maxLinearSpeed = .1
                    maxAngularSpeed = .3.toRad()
                }
                painter = remote
            }
            startCollisionPredictingModule(
                commandIn = commandToSwitch.outputs[0],
                exception = exceptions,
                lidarSet = lidarSet,
                robotOutline = robotOutline
            ) {

            }
            launch {
                for (e in exceptions)
                    exceptionServer.update(e)
            }
            launch {
                for (command in commandToSwitch.outputs[1])
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
                        val (t, quality) = filter.quality
                        buildString {
                            appendln("particles last update ${System.currentTimeMillis() - t}ms ago")
                            appendln("now system is ${if (filter.isConvergent) "" else "not"} ready for work")
                            appendln("quality = $quality")
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
}
