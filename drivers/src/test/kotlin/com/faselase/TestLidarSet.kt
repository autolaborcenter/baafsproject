package com.faselase

import com.faselase.FaselaseLidarSetBuilderDsl.Companion.startFaselaseLidarSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.common.Odometry.Companion.odometry
import org.mechdancer.networksInfo
import org.mechdancer.paintVectors
import org.mechdancer.remote.presets.remoteHub
import kotlin.math.PI

fun main() = runBlocking<Unit>(Dispatchers.Default) {
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
            delay(2000L)
        }
    }
    val set = startFaselaseLidarSet {
        lidar("/dev/pos3") {
            tag = "FrontLidar"
            pose = odometry(.113, 0, PI / 2)
            inverse = false
        }
        lidar("/dev/pos4") {
            tag = "BackLidar"
            pose = odometry(-.138, 0, PI / 2)
            inverse = false
        }
    }!!
    launch {
        while (true) {
            val frame = set.frame
            println(frame.size)
            remote.paintVectors("雷达", frame)
            delay(100L)
        }
    }
}
