package com.faselase

import com.faselase.FaselaseLidarSetBuilderDsl.Companion.startFaselaseLidarSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.mechdancer.algebra.function.vector.times
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.channel
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.geometry.angle.toVector
import org.mechdancer.networksInfo
import org.mechdancer.paintVectors
import org.mechdancer.remote.presets.remoteHub
import kotlin.math.PI

fun circle(r: Double) =
    List(64 + 1) { i -> (i * 2 * PI / 32).toRad().toVector() * r }

fun main() = runBlocking(Dispatchers.Default) {
    val remote = remoteHub("测试雷达组").apply {
        openAllNetworks()
        println(networksInfo())
    }
    launch {
        while (true) {
            remote.paintVectors("10cm", circle(.10))
            remote.paintVectors("15cm", circle(.15))
            remote.paintVectors("20cm", circle(.20))
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
    }
    for (points in lidarPointsOnRobot) {
        println("size = ${points.size}")
        remote.paintVectors("雷达", points)
    }
}
