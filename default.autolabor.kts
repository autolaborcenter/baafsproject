package cn.autolabor.baafs

import cn.autolabor.baafs.CollisionPredictingModuleBuilderDsl.Companion.startCollisionPredictingModule
import cn.autolabor.business.Business.Functions.Following
import cn.autolabor.business.BusinessBuilderDsl.Companion.startBusiness
import cn.autolabor.business.parseFromConsole
import cn.autolabor.business.registerBusinessParser
import cn.autolabor.localplanner.PotentialFieldLocalPlannerBuilderDsl.Companion.potentialFieldLocalPlanner
import cn.autolabor.locator.LocationFusionModuleBuilderDsl.Companion.startLocationFusion
import cn.autolabor.pathfollower.Commander
import cn.autolabor.pathfollower.FollowCommand
import cn.autolabor.pathfollower.PathFollowerBuilderDsl.Companion.pathFollower
import cn.autolabor.pathfollower.Proportion
import cn.autolabor.pm1.ChassisBuilderDsl.Companion.startPM1Chassis
import cn.autolabor.pm1.model.ControlVariable
import com.faselase.FaselaseLidarSetBuilderDsl.Companion.faselaseLidarSet
import com.marvelmind.MobileBeaconModuleBuilderDsl.Companion.startMobileBeacon
import kotlinx.coroutines.*
import org.mechdancer.YChannel
import org.mechdancer.algebra.core.Vector
import org.mechdancer.algebra.function.vector.*
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.algebra.implement.vector.vector2DOfZero
import org.mechdancer.channel
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.common.shape.Circle
import org.mechdancer.common.shape.Polygon
import org.mechdancer.common.toTransformation
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
// 任务
try {
    runBlocking(Dispatchers.Default) {
        // 连接外设
        // 连接底盘
        println("trying to connect to pm1 chassis...")
        val chassis =
            startPM1Chassis(robotOnOdometry.input) {
                odometryInterval = 40L
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
        val lidarSet =
            faselaseLidarSet(exceptions = channel()) {
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
                exceptionOccur { chassis.target = ControlVariable.Velocity(.0, 0.toRad()) }
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
                repelWeight = .8
                stepLength = .05

                lookAhead = 8
                minRepelPointsCount = 12

                val radius = .6
                val r0 = 1 / (radius * radius)
                repel {
                    if (it.length > radius) vector2DOfZero()
                    else -it.normalize().to2D() * (it.length.pow(-2) - r0)
                }
            }
        // 循径器（虚拟光感法）
        val pathFollower =
            pathFollower {
                sensorPose = Odometry.pose(x = .3)
                lightRange = Circle(.3, 32)
                controller = Proportion(1.0)
                minTipAngle = 60.toDegree()
                minTurnAngle = 15.toDegree()
                turnThreshold = (-120).toDegree()
                maxLinearSpeed = .18
                maxAngularSpeed = .6.toRad()
                kLinearSpeed = 1.2

                painter = remote
            }
        // 指令器
        val commander =
            Commander(commandOut = commandToSwitch.input,
                      exceptions = exceptions) {
                (business.function as? Following)?.run {
                    if (loop) global.progress = .0
                    else business.cancel()
                }
            }
        // 启动循径模块
        launch {
            for ((global, progress) in globalOnRobot)
                commander(when (progress) {
                              1.0  -> FollowCommand.Finish
                              else -> pathFollower(Stamped.stamp(localPlanner.modify(global, lidarSet.frame)))
                          })
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
            for ((v, w) in commandToSwitch.outputs[1])
                if (exceptionServer.isEmpty()) {
                    val target = ControlVariable.Velocity(v, w.toRad())
                    chassis.target = target

                    remote?.run {
                        val pre = chassis.predict(target)(1000L).toTransformation()
                        val outline = robotOutline
                            .vertex
                            .asSequence()
                            .map(pre::invoke)
                            .map(Vector::to2D)
                            .toList()
                            .let(::Polygon)
                        paint("R 新轨迹预测", outline)
                    }
                }
        }
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
                while (isActive) {
                    remote.paint("R 机器人轮廓", robotOutline)
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
