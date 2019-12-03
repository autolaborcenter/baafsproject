package cn.autolabor.baafs

import cn.autolabor.baafs.CollisionPredictorBuilderDsl.Companion.collisionPredictor
import cn.autolabor.baafs.parser.parseFromConsole
import cn.autolabor.baafs.parser.registerBusinessParser
import cn.autolabor.baafs.parser.registerExceptionServerParser
import cn.autolabor.baafs.parser.registerParticleFilterParser
import cn.autolabor.business.BusinessBuilderDsl.Companion.startBusiness
import cn.autolabor.business.FollowFailedException
import cn.autolabor.localplanner.PotentialFieldLocalPlannerBuilderDsl.Companion.potentialFieldLocalPlanner
import cn.autolabor.locator.LocationFusionModuleBuilderDsl.Companion.startLocationFusion
import cn.autolabor.pathfollower.PathFollowerBuilderDsl.Companion.pathFollower
import cn.autolabor.pm1.SerialPortChassisBuilderDsl.Companion.registerPM1Chassis
import cn.autolabor.pm1.model.ControlVariable
import cn.autolabor.serialport.manager.SerialPortManager
import com.faselase.FaselaseLidarSetBuilderDsl.Companion.faselaseLidarSet
import com.marvelmind.MobileBeaconModuleBuilderDsl.Companion.startMobileBeacon
import com.usarthmi.UsartHmi
import kotlinx.coroutines.*
import org.mechdancer.*
import org.mechdancer.algebra.function.vector.*
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.algebra.implement.vector.vector2DOfZero
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.shape.Circle
import org.mechdancer.console.parser.buildParser
import org.mechdancer.core.Chassis
import org.mechdancer.exceptions.ApplicationException
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.exceptions.ExceptionServerBuilderDsl.Companion.startExceptionServer
import org.mechdancer.exceptions.device.DeviceNotExistException
import org.mechdancer.geometry.angle.toAngle
import org.mechdancer.geometry.angle.toDegree
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.remote.presets.RemoteHub
import org.mechdancer.remote.presets.remoteHub
import kotlin.math.PI
import kotlin.math.pow
import kotlin.system.exitProcess

@ExperimentalCoroutinesApi
fun main() {
    // 画图
    val remote: RemoteHub? =
        remoteHub("painter")
            .apply {
                openAllNetworks()
                println(networksInfo())
            }
    // 话题
    val exceptions = channel<ExceptionMessage>()
    val msgFromHmi = channel<String>()
    val robotOnOdometry = YChannel<Stamped<Odometry>>()
    val robotOnMap = channel<Stamped<Odometry>>()
    val beaconOnMap = channel<Stamped<Vector2D>>()
    val globalOnRobot = channel<Pair<Sequence<Odometry>, Double>>()
    val commandToSwitch = channel<ControlVariable>()

    val manager = SerialPortManager()
    val chassis: Chassis<ControlVariable> =
        manager.registerPM1Chassis(robotOnOdometry.input) {
            odometryInterval = 40L
            maxW = 45.toDegree()
        }
    while (!manager.sync());
    // 任务
    try {
        runBlocking(Dispatchers.Default) {
            println("try to connect to usart hmi")
            val hmi: UsartHmi? =
                try {
                    UsartHmi(this, msgFromHmi, "COM3")
                } catch (e: DeviceNotExistException) {
                    println("cannot find usart hmi")
                    null
                }
            println("done")
            // 连接外设
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
                    minTipAngle = 60.toDegree()
                    minTurnAngle = 15.toDegree()
                    turnThreshold = (-120).toDegree()
                    maxSpeed = .2

                    painter = remote
                }
            // 碰撞预警模块
            val predictor =
                collisionPredictor(lidarSet = lidarSet,
                                   robotOutline = robotOutline) {
                    countToContinue = 4
                    countToStop = 6
                    predictingTime = 1000L
                    painter = remote
                }
            var isEnabled = false
            var invokeTime = 0L
            // 启动循径模块
            launch {
                for ((global, progress) in globalOnRobot) {
                    invokeTime = System.currentTimeMillis()
                    // 生成控制量
                    val target =
                        if (progress == 1.0) {
                            exceptions.send(FollowFailedException.recovered())
                            ControlVariable.Physical.static
                        } else {
                            localPlanner
                                .modify(global, lidarSet.frame)
                                .let(pathFollower::invoke)
                                ?.also { exceptions.send(FollowFailedException.recovered()) }
                            ?: run {
                                exceptions.send(FollowFailedException.occurred())
                                ControlVariable.Physical.static
                            }
                        }
                    // 急停
                    if (predictor.predict(chassis.predict(target)))
                        exceptionServer.update(CollisionDetectedException.recovered())
                    else
                        exceptionServer.update(CollisionDetectedException.occurred())
                    // 转发
                    if (isEnabled && exceptionServer.isEmpty())
                        chassis.target = target
                }
            }.invokeOnCompletion { commandToSwitch.close(it) }
            println("done")
            // 指令解析器
            val parser = buildParser {
                this["coroutines"] = { coroutineContext[Job]?.children?.count() }
                this["\'"] = { isEnabled = !isEnabled; if (isEnabled) "enabled" else "disabled" }
                registerExceptionServerParser(exceptionServer, this)
                registerParticleFilterParser(particleFilter, this)
                registerBusinessParser(business, this)
            }
            launch { while (isActive) parser.parseFromConsole() }
            hmi?.run {
                launch {
                    for (msg in msgFromHmi) {
                        if (!particleFilter.isConvergent)
                            write("location system is not ready")
                        else
                            write(parser(msg).single().second.toString())
                    }
                }
            }
            // 刷新固定显示
            if (remote != null) {
                launch {
                    while (isActive) {
                        remote.paint("R 机器人轮廓", robotOutline)
                        delay(5000L)
                    }
                }
                launch {
                    while (isActive) {
                        while (System.currentTimeMillis() - invokeTime > 2000L) {
                            remote.paintVectors("R 雷达", lidarSet.frame)
                            delay(100L)
                        }
                        delay(5000L)
                    }
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
}
