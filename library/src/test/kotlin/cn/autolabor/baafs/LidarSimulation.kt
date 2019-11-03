package cn.autolabor.baafs

import kotlinx.coroutines.*
import org.mechdancer.*
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.common.shape.Circle
import org.mechdancer.common.shape.Polygon
import org.mechdancer.common.toTransformation
import org.mechdancer.device.PolarFrameCollectorQueue
import org.mechdancer.geometry.angle.toDegree
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.remote.modules.multicast.multicastListener
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.remote.protocol.SimpleInputStream
import org.mechdancer.simulation.Chassis
import org.mechdancer.simulation.Lidar
import org.mechdancer.simulation.random.Normal
import org.mechdancer.simulation.speedSimulation
import java.io.DataInputStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.math.PI

private fun Polygon.transform(pose: Odometry): Polygon {
    val tf = pose.toTransformation()
    return Polygon(vertex.map { tf(it).to2D() })
}

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

    val obstacles =
        List(10) { i ->
            listOf(Circle(.14, 32).sample().transform(Odometry.pose(i * .3, +.5)),
                   Circle(.14, 32).sample().transform(Odometry.pose(i * .3, -.5)))
        }.flatten()
    val robotOutline = Polygon(listOf(
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
    ))

    val chassis = Chassis(Stamped(0L, Odometry.pose()))
    val lidar = Lidar(.15..8.0, 3600.toDegree(), 5E-4).apply {
        initialize(.0, Odometry.pose(), 0.toRad())
    }
    val lidarOnRobot = Odometry.pose(x = .15)
    val lidarToRobot = lidarOnRobot.toTransformation()

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
    val queue = PolarFrameCollectorQueue()
    var i = 0
    for ((t, v) in speedSimulation { buffer.get() }) {
        val (_, robotOnMap) = chassis.drive(v)
        val robotToMap = robotOnMap.toTransformation()
        lidar
            .update(t * 1E-3, robotOnMap, lidarOnRobot, obstacles)
            .map { it.data }
            .forEach {
                if (it.distance.isNaN())
                    queue.refresh(it.angle)
                else
                    queue += Stamped(t, it.copy(distance = it.distance + Normal.next(sigma = 5E-3)))
            }

        remote.paint("机器人", robotOutline.transform(robotOnMap))
        if (t > i * 100) {
            ++i
            val lidarToMap = robotToMap * lidarToRobot
            val points =
                queue.get()
                    .map { (_, polar) ->
                        val (x, y) = lidarToMap(polar.toVector2D()).to2D()
                        x to y
                    }
            remote.paintFrame2("雷达", points)
        }
    }
}
