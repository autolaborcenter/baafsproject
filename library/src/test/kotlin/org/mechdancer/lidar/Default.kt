package org.mechdancer.lidar

import cn.autolabor.baafs.outlineFilter
import cn.autolabor.baafs.robotOutline
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.channel
import org.mechdancer.common.Velocity
import org.mechdancer.common.shape.Circle
import org.mechdancer.common.shape.Polygon
import org.mechdancer.geometry.angle.toDegree
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.geometry.transformation.Pose2D
import org.mechdancer.geometry.transformation.pose2D
import org.mechdancer.geometry.transformation.toTransformation
import org.mechdancer.lidar.Default.cover
import org.mechdancer.lidar.Default.remote
import org.mechdancer.networksInfo
import org.mechdancer.paint
import org.mechdancer.remote.modules.multicast.multicastListener
import org.mechdancer.remote.presets.RemoteHub
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.remote.protocol.SimpleInputStream
import org.mechdancer.simulation.Lidar
import java.io.DataInputStream
import kotlin.concurrent.thread

internal object Default {
    val commands = channel<Velocity.NonOmnidirectional>()
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
        openAllNetworks {
            println(it.displayName)
            true
        }
        println(networksInfo())
        thread(isDaemon = true) { while (true) invoke() }
    }

    // 镜像点列表
    private fun List<Vector2D>.mirror() =
        this + this.map { (x, y) -> Vector2D(-x, y) }.reversed()

    // 机器人内的遮挡
    val cover =
        listOf(Circle(.07).sample().transform(pose2D(-.36)),
               listOf(Vector2D(-.10, +.26),
                      Vector2D(-.10, +.20),
                      Vector2D(-.05, +.20),
                      Vector2D(-.05, -.20),
                      Vector2D(-.10, -.20),
                      Vector2D(-.10, -.26)
               ).mirror().let(::Polygon))

    // 构造仿真雷达
    fun simulationLidar(onRobot: Pose2D) =
        SimulationLidar(
            lidar = Lidar(.15..8.0, 3600.toDegree(), 2E-4)
                .apply { initialize(.0, pose2D(), 0.toRad()) },
            onRobot = onRobot,
            cover = cover,
            errorSigma = 1E-2)
}

internal fun Polygon.transform(pose: Pose2D): Polygon {
    val tf = pose.toTransformation()
    return Polygon(vertex.map { tf(it).to2D() })
}

internal fun RemoteHub.paintRobot(robotOnMap: Pose2D) {
    // 绘制机器人外轮廓和遮挡物
    cover
        .map { it.transform(robotOnMap) }
        .forEachIndexed { i, polygon ->
            remote.paint("机器人遮挡$i", polygon)
        }
    remote.paint("机器人", robotOutline.transform(robotOnMap))
    remote.paint("过滤范围", outlineFilter.transform(robotOnMap))
}
