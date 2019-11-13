package cn.autolabor.baafs

import cn.autolabor.ChassisModuleBuilderDsl.Companion.startChassis
import cn.autolabor.baafs.CollisionPredictingModuleBuilderDsl.Companion.startCollisionPredictingModule
import cn.autolabor.business.Business.Functions.Following
import cn.autolabor.business.BusinessBuilderDsl.Companion.startBusiness
import cn.autolabor.business.parseFromConsole
import cn.autolabor.business.registerBusinessParser
import cn.autolabor.localplanner.PotentialFieldLocalPlannerBuilderDsl.Companion.potentialFieldLocalPlanner
import cn.autolabor.locator.LocationFusionModuleBuilderDsl.Companion.startLocationFusion
import cn.autolabor.pathfollower.CommanderBuilderDsl.Companion.commander
import cn.autolabor.pathfollower.PathFollowerBuilderDsl.Companion.pathFollower
import cn.autolabor.pathfollower.Proportion
import com.faselase.FaselaseLidarSetBuilderDsl.Companion.faselaseLidarSet
import com.marvelmind.MobileBeaconModuleBuilderDsl.Companion.startMobileBeacon
import kotlinx.coroutines.*
import org.mechdancer.YChannel
import org.mechdancer.algebra.function.vector.euclid
import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.channel
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity.Companion.velocity
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.common.shape.Circle
import org.mechdancer.common.shape.Ellipse
import org.mechdancer.console.parser.buildParser
import org.mechdancer.exceptions.ApplicationException
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.exceptions.ExceptionServerBuilderDsl.Companion.startExceptionServer
import org.mechdancer.geometry.angle.toAngle
import org.mechdancer.geometry.angle.toDegree
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.networksInfo
import org.mechdancer.paint
import org.mechdancer.remote.presets.RemoteHub
import org.mechdancer.remote.presets.remoteHub
import kotlin.math.PI
import kotlin.system.exitProcess

// 画图
val remote: RemoteHub? =
    remoteHub("painter")
        .apply {
            openAllNetworks()
            println(networksInfo())
        }
// 话题
val exceptions = channel<ExceptionMessage>()
val robotOnOdometry = YChannel<Stamped<Odometry>>()
val robotOnMap = channel<Stamped<Odometry>>()
val beaconOnMap = channel<Stamped<Vector2D>>()
val globalOnRobot = channel<Pair<Sequence<Odometry>, Double>>()
val commandToSwitch = YChannel<NonOmnidirectional>()
val commandToRobot = channel<NonOmnidirectional>()
// 任务
try {
    runBlocking(Dispatchers.Default) {
        // 连接外设
        // 连接底盘
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
        // 连接定位标签
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
            heightRange = -3.0..0.0
        }
        println("done")
        // 连接激光雷达
        println("trying to connect to faselase lidars...")
        val lidarSet = faselaseLidarSet(exceptions = channel()) {
            launchTimeout = 5000L
            connectionTimeout = 800L
            dataTimeout = 400L
            retryInterval = 100L
            lidar(port = "/dev/pos3") {
                tag = "FrontLidar"
                pose = Odometry.pose(.113, 0, PI / 2)
                inverse = false
            }
            lidar(port = "/dev/pos4") {
                tag = "BackLidar"
                pose = Odometry.pose(-.138, 0, PI / 2)
                inverse = false
            }
            val wonder = vector2DOf(+.12, -.14)
            filter { p ->
                p euclid wonder > .05 && p !in outlineFilter
            }
        }
        println("done")
        // 启动服务
        println("staring data process modules...")
        // 启动异常服务器
        val exceptionServer =
            startExceptionServer(exceptions) {
                exceptionOccur { launch { commandToRobot.send(velocity(.0, .0)) } }
            }
        // 启动定位融合模块（粒子滤波器）
        val particleFilter =
            startLocationFusion(
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
        // 启动业务交互后台
        val business =
            startBusiness(
                    robotOnMap = robotOnMap,
                    globalOnRobot = globalOnRobot
            ) {
                localRadius = .5
                pathInterval = .05
                localFirst {
                    it.p.norm() < localRadius
                    && it.p.toAngle().asRadian() in -PI / 3..+PI / 3
                    && it.d.asRadian() in -PI / 3..+PI / 3
                }
                painter = remote
            }
        // 局部规划器（势场法）
        val localPlanner =
            potentialFieldLocalPlanner {
                attractRange = Ellipse(.36, .8)
                repelRange = Ellipse(.50, .75)
                stepLength = .05
                attractWeight = 36.0
            }
        // 循径器（虚拟光感法）
        val pathFollower =
            pathFollower {
                sensorPose = Odometry.pose(x = .2)
                lightRange = Circle(.24, 16)
                controller = Proportion(1.0)
                minTipAngle = 60.toDegree()
                minTurnAngle = 15.toDegree()
                maxLinearSpeed = .16
                maxAngularSpeed = .5.toRad()

                painter = remote
            }
        // 指令器
        val commander =
            commander(
                    robotOnOdometry = robotOnOdometry.outputs[1],
                    commandOut = commandToSwitch.input,
                    exceptions = exceptions
            ) {
                directionLimit = (-120).toDegree()
                onFinish {
                    (business.function as? Following)?.run {
                        if (loop) global.progress = .0
                        else business.cancel()
                    }
                }
            }
        // 启动循径模块
        launch {
            for ((global, progress) in globalOnRobot)
                commander(pathFollower(localPlanner.modify(global, lidarSet.frame), progress))
        }.invokeOnCompletion { commandToSwitch.input.close(it) }
        // 启动碰撞预警模块
        startCollisionPredictingModule(
                commandIn = commandToSwitch.outputs[0],
                exception = exceptions,
                lidarSet = lidarSet,
                robotOutline = robotOutline
        ) {
            countToContinue = 4
            countToStop = 6
            predictingTime = 1000L
            painter = remote
        }
        // 启动指令转发
        launch {
            for (command in commandToSwitch.outputs[1])
                if (exceptionServer.isEmpty())
                    commandToRobot.send(command)
        }.invokeOnCompletion { commandToRobot.close(it) }
        println("done")
        // 指令解析器
        val parser = buildParser {
            this["coroutines"] = { coroutineContext[Job]?.children?.count() }
            this["exceptions"] = { exceptionServer.get().joinToString("\n") }
            this["fusion state"] = {
                buildString {
                    val now = System.currentTimeMillis()
                    appendln(particleFilter.lastQuery
                                 ?.let { (t, pose) -> "last locate at $pose ${now - t}ms ago" }
                             ?: "never query pose before")
                    val (t, quality) = particleFilter.quality
                    appendln("particles last update ${now - t}ms ago")
                    appendln("now system is ${if (particleFilter.isConvergent) "" else "not "}ready for work")
                    append("quality = $quality")
                }
            }
            this["\'"] = {
                (business.function as? Following)?.let {
                    commander.isEnabled = !commander.isEnabled
                    if (commander.isEnabled) "continue" else "pause"
                } ?: "cannot set enabled unless when following"
            }
            registerBusinessParser(business, this)
        }
        launch { while (isActive) parser.parseFromConsole() }
        // 刷新固定显示
        if (remote != null)
            launch {
                val (a, r) = localPlanner.sampleArea()
                while (isActive) {
                    remote.paint("R 机器人轮廓", robotOutline)
                    remote.paint("R 引力区域", a)
                    remote.paint("R 斥力区域", r)
                    delay(5000L)
                }
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
