package org.mechdancer.lidar

import cn.autolabor.baafs.CollisionPredictingModuleBuilderDsl.Companion.startCollisionPredictingModule
import cn.autolabor.baafs.outlineFilter
import cn.autolabor.baafs.robotOutline
import cn.autolabor.business.Business.Functions.Following
import cn.autolabor.business.BusinessBuilderDsl.Companion.startBusiness
import cn.autolabor.business.parseFromConsole
import cn.autolabor.business.registerBusinessParser
import cn.autolabor.localplanner.PotentialFieldLocalPlannerBuilderDsl.Companion.potentialFieldLocalPlanner
import cn.autolabor.pathfollower.CommanderBuilderDsl.Companion.commander
import cn.autolabor.pathfollower.PathFollowerBuilderDsl.Companion.pathFollower
import cn.autolabor.pathfollower.Proportion
import kotlinx.coroutines.*
import org.mechdancer.*
import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.common.shape.AnalyticalShape
import org.mechdancer.common.shape.Circle
import org.mechdancer.common.shape.Ellipse
import org.mechdancer.common.toTransformation
import org.mechdancer.console.parser.buildParser
import org.mechdancer.device.LidarSet
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.exceptions.ExceptionServerBuilderDsl.Companion.startExceptionServer
import org.mechdancer.geometry.angle.toAngle
import org.mechdancer.geometry.angle.toDegree
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.lidar.Default.commands
import org.mechdancer.lidar.Default.remote
import org.mechdancer.lidar.Default.simulationLidar
import org.mechdancer.simulation.Chassis
import org.mechdancer.simulation.speedSimulation
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI

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
    val globalOnRobot = channel<Pair<Sequence<Odometry>, Double>>()
    val commandToRobot = YChannel<NonOmnidirectional>()
    val exceptions = channel<ExceptionMessage>()
    val command = AtomicReference(Velocity.velocity(.0, .0))
    runBlocking(Dispatchers.Default) {
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
                repelRange = Ellipse(.50, .75)
                repelWeight = .025
                stepLength = .05

                lookAhead = 8
                minRepelPointsCount = 12
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
                robotOnOdometry = robotOnMap.outputs[1],
                commandOut = commandToRobot.input,
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
        }.invokeOnCompletion { commandToRobot.input.close(it) }
        // 启动碰撞预警模块
        startCollisionPredictingModule(
            commandIn = commandToRobot.outputs[0],
            exception = exceptions,
            lidarSet = lidarSet,
            robotOutline = robotOutline
        ) {
            predictingTime = 1000L
            painter = remote
        }
        // 接收指令
        launch {
            for ((v, w) in commands)
                command.set(Velocity.velocity(0.2 * v, 0.8 * w))
        }
        // 发送指令
        launch {
            val watchDog = WatchDog(this, 3 * dt) { command.set(Velocity.velocity(0, 0)) }
            for (v in commandToRobot.outputs[1]) {
                if (!exceptionServer.isEmpty()) continue
                watchDog.feed()
                command.set(v)
            }
        }
        val parser = buildParser {
            this["coroutines"] = { coroutineContext[Job]?.children?.count() }
            this["exceptions"] = { exceptionServer.get().joinToString("\n") }
            this["\'"] = {
                (business.function as? Following)?.let {
                    commander.isEnabled = !commander.isEnabled
                    if (commander.isEnabled) "continue" else "pause"
                } ?: "cannot set enabled unless when following"
            }
            registerBusinessParser(business, this)
        }
        // 处理控制台
        launch { while (isActive) parser.parseFromConsole() }
        // 刷新固定显示
        launch {
            val r = (localPlanner.repelArea as AnalyticalShape).sample()
            val o = obstacles.flatMap { it.vertex }
            while (isActive) {
                remote.paint("R 机器人轮廓", robotOutline)
                remote.paint("R 斥力区域", r)
                remote.paintVectors("障碍物", o)
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
                robotOnMap.input.send(actual)
            // 激光雷达采样
            if (lidarSampler.trySample(t)) {
                val robotToMap = actual.data.toTransformation()
                remote.paintVectors("过滤", lidarSet.frame.map { robotToMap(it).to2D() })
            }
        }
    }
}
