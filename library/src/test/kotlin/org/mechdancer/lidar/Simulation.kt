package org.mechdancer.lidar

import cn.autolabor.baafs.bussiness.Business
import cn.autolabor.baafs.bussiness.BusinessBuilderDsl.Companion.startBusiness
import cn.autolabor.baafs.bussiness.FollowFailedException
import cn.autolabor.baafs.collisionpredictor.CollisionDetectedException
import cn.autolabor.baafs.collisionpredictor.CollisionPredictorBuilderDsl.Companion.collisionPredictor
import cn.autolabor.baafs.toGridOf
import cn.autolabor.baafs.outlineFilter
import cn.autolabor.baafs.parser.parseFromConsole
import cn.autolabor.baafs.parser.registerBusinessParser
import cn.autolabor.baafs.parser.registerExceptionServerParser
import cn.autolabor.baafs.robotOutline
import cn.autolabor.pm1.model.ChassisStructure
import cn.autolabor.serialport.manager.SerialPortManager
import com.faselase.LidarSet
import com.usarthmi.UsartHmiBuilderDsl.Companion.registerUsartHmi
import kotlinx.coroutines.*
import org.mechdancer.*
import org.mechdancer.action.PathFollowerBuilderDsl.Companion.pathFollower
import org.mechdancer.algebra.function.vector.normalize
import org.mechdancer.algebra.function.vector.times
import org.mechdancer.algebra.function.vector.unaryMinus
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.algebra.implement.vector.vector2DOfZero
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity
import org.mechdancer.common.shape.Circle
import org.mechdancer.common.toTransformation
import org.mechdancer.console.parser.buildParser
import org.mechdancer.core.LocalPath
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.exceptions.ExceptionMessage.Occurred
import org.mechdancer.exceptions.ExceptionMessage.Recovered
import org.mechdancer.exceptions.ExceptionServerBuilderDsl.Companion.startExceptionServer
import org.mechdancer.geometry.angle.toAngle
import org.mechdancer.geometry.angle.toDegree
import org.mechdancer.lidar.Default.commands
import org.mechdancer.lidar.Default.remote
import org.mechdancer.lidar.Default.simulationLidar
import org.mechdancer.local.LocalPotentialFieldPlannerBuilderDsl.Companion.potentialFieldPlanner
import org.mechdancer.simulation.Chassis
import org.mechdancer.simulation.speedSimulation
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.pow

private val pot = Circle(.14, 32).sample()

private val obstacles =
    List(10) { i ->
        listOf(pot.transform(Odometry.pose(i * .3, +.5)),
               pot.transform(Odometry.pose(i * .3, -.5)),
               pot.transform(Odometry.pose(i * .3, 1.5)))
    }.flatten()

private const val T0 = 0L
private const val speed = 2
private const val frequency = 50L

private val lidarSampler = Sampler(20.0)
private val odometrySampler = Sampler(20.0)

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
fun main() {
    val dt = 1000 / frequency

    val chassis = Chassis(Stamped(T0, Odometry.pose()))
    val front = simulationLidar(Odometry.pose(x = +.113))
    val back = simulationLidar(Odometry.pose(x = -.138))
    val lidarSet =
        LidarSet(mapOf(front::frame to front.toRobot,
                       back::frame to back.toRobot)
        ) { it !in outlineFilter }

    // 话题
    val robotOnMap = YChannel<Stamped<Odometry>>()
    val globalOnRobot = channel<LocalPath>()
    val exceptions = channel<ExceptionMessage>()
    val command = AtomicReference(Velocity.velocity(.0, .0))
    val hmiMessages = channel<String>()

    val manager = SerialPortManager(exceptions)
    val hmi = manager.registerUsartHmi(hmiMessages)
    manager.sync()

    runBlocking(Dispatchers.IO) {
        val exceptionServer =
            startExceptionServer(exceptions) {
                exceptionOccur { command.set(Velocity.velocity(.0, .0)) }
            }
        // 启动业务交互后台
        val business =
            startBusiness(
                    robotOnMap = robotOnMap.outputs[0],
                    globalOnRobot = globalOnRobot
            ) {
                localRadius = .3
                pathInterval = .05
                localFirst {
                    it.p.length < .01
                    || (it.p.length < localRadius
                        && it.p.toAngle().asRadian() in -PI / 3..+PI / 3
                        && it.d.asRadian() in -PI / 3..+PI / 3)
                }
            }
        var obstacleFrame = emptyList<Vector2D>()
        // 局部规划器（势场法）
        val localPlanner =
            potentialFieldPlanner {
                repelWeight = .5
                stepLength = .05

                lookAhead = 8
                minRepelPointsCount = 12

                val radius = .5
                val r0 = 1 / (radius * radius)
                repel {
                    if (it.length > radius) vector2DOfZero()
                    else -it.normalize().to2D() * (it.length.pow(-2) - r0)
                }

                obstacles {
                    obstacleFrame = lidarSet.frame.toGridOf(vector2DOf(.05, .05))
                    remote.paintVectors("R 聚类", obstacleFrame)
                    obstacleFrame
                }
            }
        // 循径器（虚拟光感法）
        val pathFollower =
            pathFollower {
                sensorPose = Odometry.pose(x = .3)
                lightRange = Circle(.3, 32)
                minTipAngle = 60.toDegree()
                minTurnAngle = 15.toDegree()
                maxSpeed = .18

                painter = remote
            }
        // 碰撞预警模块
        val predictor =
            collisionPredictor(robotOutline = robotOutline) {
                countToContinue = 4
                countToStop = 6
                predictingTime = 1000L
                painter = remote

                obstacles { obstacleFrame }
            }
        var isEnabled = false
        var invokeTime = 0L
        val watchDog = WatchDog(this, 3 * dt) { command.set(Velocity.velocity(0, 0)) }
        // 启动循径模块
        launch {
            val struct = ChassisStructure(.465, .105, .105, .355)
            for (local in globalOnRobot) {
                invokeTime = System.currentTimeMillis()
                // 生成控制量
                val target =
                    localPlanner
                        .plan(local)
                        .let { pathFollower.plan(it) }
                        ?.also { exceptions.send(Recovered(FollowFailedException)) }
                        ?.let(struct::toVelocity)
                        ?.let { (v, w) -> Velocity.velocity(v, w.asRadian()) }
                    ?: run {
                        exceptions.send(Occurred(FollowFailedException))
                        Velocity.velocity(.0, .0)
                    }
                // 急停
                if (predictor.predict { target.toDeltaOdometry(it / 1000.0) })
                    exceptionServer.update(Recovered(CollisionDetectedException))
                else
                    exceptionServer.update(Occurred(CollisionDetectedException))
                // 转发
                if (isEnabled && exceptionServer.isEmpty()) {
                    watchDog.feed()
                    command.set(target)
                }
            }
        }
        // 接收指令
        launch {
            for ((v, w) in commands)
                command.set(Velocity.velocity(0.2 * v, 0.8 * w))
        }
        val parser = buildParser {
            this["coroutines"] = { coroutineContext[Job]?.children?.count() }
            this["\'"] = { isEnabled = !isEnabled; if (isEnabled) "enabled" else "disabled" }
            this["load @name"] = {
                val name = get(1).toString()
                try {
                    runBlocking(coroutineContext) { business.startFollowing(name) }
                    val path = (business.function as Business.Functions.Following).planner
                    path.painter = remote
                    "${path.size} poses loaded from $name"
                } catch (e: Exception) {
                    e.message
                }
            }
            registerExceptionServerParser(exceptionServer, this)
            registerBusinessParser(business, hmi, this)
        }
        // 处理控制台
        launch { while (isActive) parser.parseFromConsole() }
        // 刷新固定显示
        launch {
            while (isActive) {
                remote.paint("R 机器人轮廓", robotOutline)
                delay(5000L)
            }
        }
        launch {
            while (isActive) {
                while (System.currentTimeMillis() - invokeTime > 2000L) {
                    val frame = lidarSet.frame
                    remote.paintVectors("R 雷达", frame)
                    remote.paintVectors("R 聚类",  frame.toGridOf(vector2DOf(.05, .05)))
                    delay(200L)
                }
                delay(5000L)
            }
        }
        // 运行仿真
        for ((t, v) in speedSimulation(T0, dt, speed) { command.get() }) {
            // 控制机器人行驶
            val actual = chassis.drive(v, t)
            // 更新激光雷达
            front.update(actual, obstacles)
            back.update(actual, obstacles)
            // 画机器人
            remote.paintRobot(actual.data)
            remote.paintPose("实际", actual.data)
            if (odometrySampler.trySample(t))
                robotOnMap.send(actual)
            // 激光雷达采样
            if (lidarSampler.trySample(t)) {
                val robotToMap = actual.data.toTransformation()
                remote.paintVectors("过滤", lidarSet.frame.map { robotToMap(it).to2D() })
            }
        }
    }
}
