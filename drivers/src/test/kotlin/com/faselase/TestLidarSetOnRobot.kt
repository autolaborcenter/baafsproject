package com.faselase

import cn.autolabor.serialport.manager.SerialPortManager
import com.faselase.FaselaseLidarSetBuilderDsl.Companion.registerFaselaseLidarSet
import kotlinx.coroutines.*
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.channel
import org.mechdancer.common.shape.Circle
import org.mechdancer.common.shape.Polygon
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.geometry.transformation.Pose2D
import org.mechdancer.geometry.transformation.pose2D
import org.mechdancer.networksInfo
import org.mechdancer.paint
import org.mechdancer.paintVectors
import org.mechdancer.remote.presets.remoteHub
import kotlin.math.PI

private fun Polygon.transform(pose: Pose2D) =
    Polygon(vertex.map(pose::times))

@ObsoleteCoroutinesApi
fun main() {
    val remote = remoteHub("测试雷达组").apply {
        openAllNetworks()
        println(networksInfo())
    }
    val robotOutline = Polygon(listOf(
        Vector2D(+.25, +.08),
        Vector2D(+.10, +.20),
        Vector2D(+.10, +.28),
        Vector2D(-.10, +.28),
        Vector2D(-.10, +.23),
        Vector2D(-.25, +.23),
        Vector2D(-.47, +.20),
        Vector2D(-.47, -.20),
        Vector2D(-.25, -.23),
        Vector2D(-.10, -.23),
        Vector2D(-.10, -.28),
        Vector2D(+.10, -.28),
        Vector2D(+.10, -.20),
        Vector2D(+.25, -.08)
    ))
    GlobalScope.launch {
        val blindA = Circle(.15).sample().transform(pose2D(+.113))
        val blindB = Circle(.15).sample().transform(pose2D(-.138))
        while (true) {
            remote.paint("轮廓", robotOutline)
            remote.paint("前雷达盲区", blindA)
            remote.paint("后雷达盲区", blindB)
            delay(2000L)
        }
    }
    val exceptions = channel<ExceptionMessage>()
    val manager = SerialPortManager(exceptions)
    val lidarSet =
        manager.registerFaselaseLidarSet(exceptions) {
            dataTimeout = 400L
            lidar(port = "/dev/pos3") {
                tag = "FrontLidar"
                pose = pose2D(.113, 0, PI / 2)
                inverse = false
            }
            lidar(port = "/dev/pos4") {
                tag = "BackLidar"
                pose = pose2D(-.138, 0, PI / 2)
                inverse = false
            }
            filter { p ->
                p !in robotOutline
            }
        }
    while (manager.sync().isNotEmpty())
        Thread.sleep(100L)
    runBlocking {
        while (true) {
            val points = lidarSet.frame
            println("size = ${points.size}")
            remote.paintVectors("雷达", points)
            delay(100L)
        }
    }
}
