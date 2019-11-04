package org.mechdancer.lidar

import cn.autolabor.baafs.outlineFilter
import cn.autolabor.baafs.robotOutline
import kotlinx.coroutines.*
import org.mechdancer.*
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.common.shape.Circle
import org.mechdancer.common.toTransformation
import org.mechdancer.device.LidarSet
import org.mechdancer.geometry.angle.toDegree
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.remote.modules.multicast.multicastListener
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.remote.protocol.SimpleInputStream
import org.mechdancer.simulation.Chassis
import org.mechdancer.simulation.Lidar
import org.mechdancer.simulation.speedSimulation
import java.io.DataInputStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.math.PI

private val obstacles =
    List(10) { i ->
        listOf(Circle(.14, 32).sample().transform(Odometry.pose(i * .3, +.5)),
               Circle(.14, 32).sample().transform(Odometry.pose(i * .3, -.5)))
    }.flatten()

private fun simulationLidar(onRobot: Odometry) =
    SimulationLidar(
            lidar = Lidar(.15..8.0, 3600.toDegree(), 2E-4)
                .apply { initialize(.0, Odometry.pose(), 0.toRad()) },
            onRobot = onRobot,
            cover = cover,
            errorSigma = 1E-2)

@ExperimentalCoroutinesApi
fun main() = runBlocking(Dispatchers.Default) {
    val commands = channel<NonOmnidirectional>()
    val remote = remoteHub("simulator") {
        inAddition {
            multicastListener { _, _, payload ->
                if (payload.size == 16)
                    GlobalScope.launch {
                        val stream = DataInputStream(SimpleInputStream(payload))
                        @Suppress("BlockingMethodInNonBlockingContext")
                        commands.send(Velocity.velocity(stream.readDouble(), stream.readDouble()))
                    }
            }
        }
    }.apply {
        openAllNetworks()
        println(networksInfo())
        thread(isDaemon = true) { while (true) invoke() }
    }

    val chassis = Chassis(Stamped(0L, Odometry.pose()))
    val front = simulationLidar(Odometry.pose(x = +.113))
    val back = simulationLidar(Odometry.pose(x = -.138))
    val lidarSet =
        LidarSet(mapOf(front::frame to front.toRobot,
                       back::frame to back.toRobot)
        ) { it !in outlineFilter }

    val buffer = AtomicReference<NonOmnidirectional>(Velocity.velocity(0, 0))
    launch {
        for (command in commands)
            buffer.set(Velocity.velocity(0.2 * command.v, PI / 5 * command.w))
    }
    launch {
        while (true) {
            remote.paintVectors("障碍物", obstacles.flatMap { it.vertex })
            delay(5000L)
        }
    }
    // 激光雷达采样
    val lidarSamplePeriod = 50L
    var lidarSampleCount = 0
    // 运行仿真
    for ((t, v) in speedSimulation { buffer.get() }) {
        // 控制机器人行驶
        val (_, robotOnMap) = chassis.drive(v)
        // 更新激光雷达
        front.update(t, robotOnMap, obstacles)
        back.update(t, robotOnMap, obstacles)
        // 绘制机器人外轮廓和遮挡物
        cover.map { it.transform(robotOnMap) }
            .forEachIndexed { i, polygon ->
                remote.paint("机器人遮挡$i", polygon)
            }
        remote.paint("机器人", robotOutline.transform(robotOnMap))
        // 激光雷达采样
        if (t > lidarSampleCount * lidarSamplePeriod) {
            ++lidarSampleCount
            val robotToMap = robotOnMap.toTransformation()
            val frontLidarToMap = robotToMap * front.toRobot
            val frontPoints =
                front.frame
                    .map { (_, polar) ->
                        val (x, y) = frontLidarToMap(polar.toVector2D()).to2D()
                        x to y
                    }
            val backLidarToMap = robotToMap * back.toRobot
            val backPoints =
                back.frame
                    .map { (_, polar) ->
                        val (x, y) = backLidarToMap(polar.toVector2D()).to2D()
                        x to y
                    }
            val filteredPoints =
                lidarSet.frame
                    .map {
                        val (x, y) = robotToMap(it).to2D()
                        x to y
                    }
            remote.paintFrame2("前雷达", frontPoints)
            remote.paintFrame2("后雷达", backPoints)
            remote.paintFrame2("过滤", filteredPoints)
        }
    }
}
