package org.mechdancer.lidar

import cn.autolabor.baafs.outlineFilter
import com.faselase.LidarSet
import kotlinx.coroutines.*
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.common.shape.Circle
import org.mechdancer.common.toTransformation
import org.mechdancer.lidar.Default.commands
import org.mechdancer.lidar.Default.remote
import org.mechdancer.lidar.Default.simulationLidar
import org.mechdancer.paintFrame2
import org.mechdancer.paintVectors
import org.mechdancer.simulation.Chassis
import org.mechdancer.simulation.speedSimulation
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI

private val obstacles =
    List(10) { i ->
        listOf(Circle(.14, 32).sample().transform(Odometry.pose(i * .3, +.5)),
               Circle(.14, 32).sample().transform(Odometry.pose(i * .3, -.5)))
    }.flatten()

@ExperimentalCoroutinesApi
fun main() = runBlocking(Dispatchers.Default) {
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
        val actual = chassis.drive(v)
        // 更新激光雷达
        front.update(actual, obstacles)
        back.update(actual, obstacles)
        remote.paintRobot(actual.data)
        // 激光雷达采样
        if (t > lidarSampleCount * lidarSamplePeriod) {
            ++lidarSampleCount
            val robotToMap = actual.data.toTransformation()
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
