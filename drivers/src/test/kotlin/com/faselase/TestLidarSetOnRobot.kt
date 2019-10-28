package com.faselase

import com.faselase.FaselaseLidarSetBuilderDsl.Companion.startFaselaseLidarSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.mechdancer.algebra.function.vector.plus
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.channel
import org.mechdancer.common.Odometry.Companion.odometry
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.networksInfo
import org.mechdancer.paintVectors
import org.mechdancer.remote.presets.remoteHub
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
        vector2DOf(+.25, -.08),
        vector2DOf(+.25, +.08))
    launch {
        while (true) {
            remote.paintVectors("轮廓", robotOutline)
            remote.paintVectors("前雷达盲区", circle(.15).map { it + vector2DOf(+.113, 0) })
            remote.paintVectors("后雷达盲区", circle(.15).map { it + vector2DOf(-.138, 0) })
            delay(2000L)
        }
    }
    val exceptions = channel<ExceptionMessage>()
    val lidarPointsOnRobot = channel<List<Vector2D>>()
    startFaselaseLidarSet(
        points = lidarPointsOnRobot,
        exceptions = exceptions
    ) {
        launchTimeout = 5000L
        connectionTimeout = 3000L
        dataTimeout = 2000L
        retryInterval = 100L
        period = 100L
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
    }
    for (points in lidarPointsOnRobot) {
        println("size = ${points.size}")
        remote.paintVectors("雷达", points)
    }
}
