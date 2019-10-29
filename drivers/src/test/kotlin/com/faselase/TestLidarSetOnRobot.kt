package com.faselase

import com.faselase.FaselaseLidarSetBuilderDsl.Companion.faselaseLidarSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.mechdancer.algebra.function.vector.plus
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.channel
import org.mechdancer.common.Odometry.Companion.odometry
import org.mechdancer.networksInfo
import org.mechdancer.paint
import org.mechdancer.paintVectors
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.shape.Circle
import org.mechdancer.shape.Shape
import kotlin.math.PI

fun main() = runBlocking(Dispatchers.Default) {
    val remote = remoteHub("测试雷达组").apply {
        openAllNetworks()
        println(networksInfo())
    }
    val robotOutline = listOf(
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
    ).let(::Shape)
    launch {
        while (true) {
            remote.paint("轮廓", robotOutline)
            remote.paintVectors("前雷达盲区", Circle(.15).vertex.map { it + vector2DOf(+.113, 0) })
            remote.paintVectors("后雷达盲区", Circle(.15).vertex.map { it + vector2DOf(-.138, 0) })
            delay(2000L)
        }
    }
    val lidarSet = faselaseLidarSet(exceptions = channel()) {
        launchTimeout = 5000L
        connectionTimeout = 800L
        dataTimeout = 400L
        retryInterval = 100L
        lidar(port = "/dev/pos3") {
            tag = "FrontLidar"
            pose = odometry(.113, 0, PI / 2)
            inverse = false
        }
        lidar(port = "/dev/pos4") {
            tag = "BackLidar"
            pose = odometry(-.138, 0, PI / 2)
            inverse = false
        }
        filter { p ->
            p !in robotOutline
        }
    }
    while (true) {
        val points = lidarSet.frame
        println("size = ${points.size}")
        remote.paintVectors("雷达", points)
        delay(100L)
    }
}
