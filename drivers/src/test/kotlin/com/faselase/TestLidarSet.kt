package com.faselase

import com.faselase.FaselaseLidarSetBuilderDsl.Companion.faselaseLidarSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.channel
import org.mechdancer.networksInfo
import org.mechdancer.paint
import org.mechdancer.paintVectors
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.simulation.map.shape.Circle
import org.mechdancer.simulation.map.shape.Polygon

fun main() = runBlocking(Dispatchers.Default) {
    val remote = remoteHub("测试雷达组").apply {
        openAllNetworks()
        println(networksInfo())
    }
    val blind = Polygon(listOf(
        vector2DOf(+.0, +.0),
        vector2DOf(+.0, +.3),
        vector2DOf(+.3, +.3),
        vector2DOf(+.2, -.3)
    ))
    launch {
        val `10cm` = Circle(.10).sample().toList().let(::Polygon)
        val `15cm` = Circle(.15).sample().toList().let(::Polygon)
        val `20cm` = Circle(.20).sample().toList().let(::Polygon)
        while (true) {
            remote.paint("过滤区", blind)
            remote.paint("10cm", `10cm`)
            remote.paint("15cm", `15cm`)
            remote.paint("20cm", `20cm`)
            delay(2000L)
        }
    }
    val lidarSet = faselaseLidarSet(exceptions = channel()) {
        launchTimeout = 5000L
        connectionTimeout = 800L
        dataTimeout = 400L
        retryInterval = 100L
        lidar {
            tag = "Lidar"
            inverse = true
        }
        filter { p ->
            p !in blind
        }
    }
    while (true) {
        val points = lidarSet.frame
        println("size = ${points.size}")
        remote.paintVectors("雷达", points)
        delay(100L)
    }
}
