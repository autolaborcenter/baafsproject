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
import org.mechdancer.device.LidarSet
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

private class SimulationLidar(
    private val lidar: Lidar,
    private val onRobot: Odometry,
    private val cover: List<Polygon>
) {
    private val queue = PolarFrameCollectorQueue()

    val toRobot = onRobot.toTransformation()
    val frame get() = queue.get()

    fun update(t: Long, robotOnMap: Odometry, obstacles: List<Polygon>) {
        lidar
            .update(t * 1E-3, robotOnMap, onRobot, cover, obstacles)
            .map { it.data }
            .forEach {
                if (it.distance.isNaN())
                    queue.refresh(it.angle)
                else
                    queue += Stamped(t, it.copy(distance = it.distance * Normal.next(expect = 1.0, sigma = 1E-2)))
            }
    }
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
            listOf(
                Circle(.14, 32).sample().transform(Odometry.pose(i * .3, +.5)),
                Circle(.14, 32).sample().transform(Odometry.pose(i * .3, -.5)))
        }.flatten()
    val cover =
        listOf(
            Circle(.07).sample().transform(Odometry.pose(-.36)),
            Polygon(
                listOf(
                    vector2DOf(-.10, +.26),
                    vector2DOf(-.10, +.20),
                    vector2DOf(-.05, +.20),
                    vector2DOf(-.05, -.20),
                    vector2DOf(-.10, -.20),
                    vector2DOf(-.10, -.26),
                    vector2DOf(+.10, -.26),
                    vector2DOf(+.10, -.20),
                    vector2DOf(+.05, -.20),
                    vector2DOf(+.05, +.20),
                    vector2DOf(+.10, +.20),
                    vector2DOf(+.10, +.26))))

    val chassis = Chassis(Stamped(0L, Odometry.pose()))
    val front = SimulationLidar(
        Lidar(.15..8.0, 3600.toDegree(), 2E-4).apply {
            initialize(.0, Odometry.pose(), 0.toRad())
        }, Odometry.pose(x = +.113), cover)
    val back = SimulationLidar(
        Lidar(.15..8.0, 3600.toDegree(), 2E-4).apply {
            initialize(.0, Odometry.pose(), 0.toRad())
        }, Odometry.pose(x = -.138), cover)
    val lidarSet = LidarSet(
        mapOf(
            front::frame to front.toRobot,
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
    val lidarSamplePeriod = 50L
    var lidarSampleCount = 0
    for ((t, v) in speedSimulation { buffer.get() }) {
        val (_, robotOnMap) = chassis.drive(v)
        front.update(t, robotOnMap, obstacles)
        back.update(t, robotOnMap, obstacles)

        cover.map { it.transform(robotOnMap) }
            .forEachIndexed { i, polygon ->
                remote.paint("机器人遮挡$i", polygon)
            }
        remote.paint("机器人", robotOutline.transform(robotOnMap))
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
