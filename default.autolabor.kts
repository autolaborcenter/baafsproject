package cn.autolabor.baafs

import cn.autolabor.ChassisModuleBuilderDsl.Companion.startChassis
import cn.autolabor.baafs.LinkMode.Direct
import cn.autolabor.core.server.DefaultSetup
import cn.autolabor.core.server.ServerManager
import cn.autolabor.locator.LocationFusionModuleBuilderDsl.Companion.startLocationFusion
import cn.autolabor.pathfollower.PathFollowerModuleBuilderDsl.Companion.startPathFollower
import cn.autolabor.pathfollower.parseFromConsole
import cn.autolabor.pathfollower.shape.Circle
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
import org.mechdancer.geometry.angle.toDegree
import org.mechdancer.networksInfo
import org.mechdancer.remote.presets.remoteHub
import kotlin.math.PI

ServerManager.setSetup(object : DefaultSetup() {
    override fun start() = Unit
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
val commandToObstacle = channel<NonOmnidirectional>()
val commandToRobot = channel<NonOmnidirectional>()
val parser = buildParser { }
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
            beaconOnMap = beaconOnMap
        ) {
            port = null
            retryInterval = 100L
            retryTimes = 3
            connectionTimeout = 2000L
            dataTimeout = 2000L
            delayLimit = 400L
        }
        println("done")

        println("trying to connect to faselase lidars...")
        startObstacleAvoiding(
            mode = Direct,
            commandIn = commandToObstacle,
            commandOut = commandToRobot)
        println("done")

        startLocationFusion(
            robotOnOdometry = robotOnOdometry.outputs[0],
            beaconOnMap = beaconOnMap,
            robotOnMap = robotOnMap
        ) {
            filter {
                beaconOnRobot = vector2DOf(-.01, 0)
                beaconWeight = .15 * count
            }
            painter = remote
        }
        startPathFollower(
            robotOnMap = robotOnMap,
            robotOnOdometry = robotOnOdometry.outputs[1],
            commandOut = commandToObstacle,
            consoleParser = parser
        ) {
            pathInterval = .05
            directionLimit = (-120).toDegree()
            follower {
                sensorPose = odometry(.275, 0)
                lightRange = Circle(.3)
                minTipAngle = PI / 3
                minTurnAngle = PI / 12
                maxJumpCount = 20
                maxLinearSpeed = .12
                maxAngularSpeed = .4
            }
        }

        parser["coroutines count"] = { coroutineContext[Job]?.children?.count() }
        GlobalScope.launch { while (isActive) parser.parseFromConsole() }
    }
} catch (e: CancellationException) {
} catch (e: ApplicationException) {
    System.err.println(e.message)
} catch (e: Throwable) {
    System.err.println("program terminate because of ${e::class.simpleName}")
    e.printStackTrace()
} finally {
    println("program stopped")
}
