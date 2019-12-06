package cn.autolabor.baafs

import cn.autolabor.baafs.CollisionPredictorBuilderDsl.Companion.collisionPredictor
import cn.autolabor.baafs.parser.parseFromConsole
import cn.autolabor.baafs.parser.registerBusinessParser
import cn.autolabor.baafs.parser.registerExceptionServerParser
import cn.autolabor.business.BusinessBuilderDsl.Companion.startBusiness
import cn.autolabor.business.FollowFailedException
import cn.autolabor.localplanner.PotentialFieldLocalPlannerBuilderDsl.Companion.potentialFieldLocalPlanner
import cn.autolabor.locator.LocationFusionModuleBuilderDsl.Companion.startLocationFusion
import cn.autolabor.pathfollower.PathFollowerBuilderDsl.Companion.pathFollower
import cn.autolabor.pm1.SerialPortChassisBuilderDsl.Companion.registerPM1Chassis
import cn.autolabor.pm1.model.ControlVariable
import cn.autolabor.serialport.manager.SerialPortManager
import com.faselase.FaselaseLidarSetBuilderDsl.Companion.registerFaselaseLidarSet
import com.faselase.LidarSet
import com.marvelmind.SerialPortMobileBeaconBuilderDsl.Companion.registerMobileBeacon
import com.usarthmi.usartHmi
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
import org.mechdancer.console.parser.feedback
import org.mechdancer.core.Chassis
import org.mechdancer.core.MobileBeacon
import org.mechdancer.exceptions.ApplicationException
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.exceptions.ExceptionMessage.Occurred
import org.mechdancer.exceptions.ExceptionMessage.Recovered
import org.mechdancer.exceptions.ExceptionServerBuilderDsl.Companion.startExceptionServer
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
    // 连接串口外设
    val manager = SerialPortManager(exceptions)
    // 配置屏幕
    val hmi = manager.usartHmi("COM3", msgFromHmi)
    // 配置底盘
    val chassis: Chassis<ControlVariable> =
        manager.registerPM1Chassis(
            robotOnOdometry = robotOnOdometry.input
        ) {
            odometryInterval = 40L
            maxAccelerate = .75
        }
    // 配置定位标签
    val beacon: MobileBeacon =
        manager.registerMobileBeacon(
            beaconOnMap = beaconOnMap,
            exceptions = exceptions
        ) {
            port = "/dev/beacon"
            dataTimeout = 5000L

            delayLimit = 400L
            heightRange = -3.0..0.0
        }
    // 配置雷达
    val lidarSet: LidarSet =
        manager.registerFaselaseLidarSet(
            exceptions = exceptions
        ) {
            dataTimeout = 400L
            lidar(port = "/dev/pos3") {
                tag = "front lidar"
                pose = Odometry.pose(.113, 0, PI / 2)
                inverse = false
            }
            lidar(port = "/dev/pos4") {
                tag = "back lidar"
                pose = Odometry.pose(-.138, 0, PI / 2)
                inverse = false
            }
            val wonder = vector2DOf(+.12, -.14)
            filter { p ->
                p euclid wonder > .05 && p !in outlineFilter
            }
        }
    // 连接串口设备
    sync@ while (true) {
        val remain = manager.sync()
        when {
            remain.isEmpty()                 -> {
                println("Every devices are ready.")
                break@sync
            }
            remain.singleOrNull() == hmi.tag -> {
                println("Screen offline.")
                break@sync
            }
            else                             -> {
                println("There are still $remain devices offline, press ENTER to sync again.")
                readLine()
            }
        }
    }
    // 任务
    try {
        runBlocking(Dispatchers.Default) {
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
                    maxSpeed = .25

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
                            exceptions.send(Recovered(FollowFailedException))
                            ControlVariable.Physical.static
                        } else {
                            localPlanner
                                .modify(global, lidarSet.frame)
                                .let(pathFollower::invoke)
                                ?.also { exceptions.send(Recovered(FollowFailedException)) }
                            ?: run {
                                exceptions.send(Occurred(FollowFailedException))
                                ControlVariable.Physical.static
                            }
                        }
                    // 急停
                    if (predictor.predict(chassis.predict(target)))
                        exceptionServer.update(Recovered(CollisionDetectedException))
                    else
                        exceptionServer.update(Occurred(CollisionDetectedException))
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
                this["beacon"] = { beacon.location }
                registerExceptionServerParser(exceptionServer, this)
//                registerParticleFilterParser(particleFilter, this)
                registerBusinessParser(business, this)
            }
            launch { while (isActive) parser.parseFromConsole() }
            launch {
                for (msg in msgFromHmi)
                    parser(msg)
                        .first()
                        .let(::feedback)
                        .second
                        .toString()
                        .let { hmi.write(it) }
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
