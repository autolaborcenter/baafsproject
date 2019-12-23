package com.faselase

import cn.autolabor.serialport.manager.SerialPortManager
import com.faselase.FaselaseLidarSetBuilderDsl.Companion.registerFaselaseLidarSet
import kotlinx.coroutines.*
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.channel
import org.mechdancer.common.Odometry
import org.mechdancer.common.Odometry.Companion.pose
import org.mechdancer.common.shape.Circle
import org.mechdancer.common.shape.Polygon
import org.mechdancer.common.toTransformation
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.networksInfo
import org.mechdancer.paint
import org.mechdancer.paintVectors
import org.mechdancer.remote.presets.remoteHub
import kotlin.math.PI

private fun Polygon.transform(pose: Odometry): Polygon {
    val tf = pose.toTransformation()
    return Polygon(vertex.map { tf(it).to2D() })
}

@ObsoleteCoroutinesApi
fun main() {
    val remote = remoteHub("测试雷达组").apply {
        openAllNetworks()
        println(networksInfo())
    }
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
    GlobalScope.launch {
        val blindA = Circle(.15).sample().transform(pose(+.113))
        val blindB = Circle(.15).sample().transform(pose(-.138))
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
                pose = pose(.113, 0, PI / 2)
                inverse = false
            }
            lidar(port = "/dev/pos4") {
                tag = "BackLidar"
                pose = pose(-.138, 0, PI / 2)
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
