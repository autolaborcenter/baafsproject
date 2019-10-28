package com.faselase

import com.faselase.FaselaseLidarSetBuilderDsl.Companion.startFaselaseLidarSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.channel
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.networksInfo
import org.mechdancer.paint
import org.mechdancer.paintVectors
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.shape.Circle
import org.mechdancer.shape.Shape

fun main() = runBlocking(Dispatchers.Default) {
    val remote = remoteHub("测试雷达组").apply {
        openAllNetworks()
        println(networksInfo())
    }
    val blind = Shape(listOf(
        vector2DOf(+.0, +.0),
        vector2DOf(+.0, +.3),
        vector2DOf(+.3, +.3),
        vector2DOf(+.2, -.3)
    ))
    launch {
        while (true) {
            remote.paint("过滤区", blind)
            remote.paint("10cm", Circle(.10))
            remote.paint("15cm", Circle(.15))
            remote.paint("20cm", Circle(.20))
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
        lidar {
            tag = "Lidar"
            inverse = true
        }
        filter { p ->
            p !in blind
        }
    }
    for (points in lidarPointsOnRobot) {
        println("size = ${points.size}")
        remote.paintVectors("雷达", points)
    }
}
