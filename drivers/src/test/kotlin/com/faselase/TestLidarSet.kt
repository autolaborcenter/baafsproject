package com.faselase

import com.faselase.FaselaseLidarSetBuilderDsl.Companion.startFaselaseLidarSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.mechdancer.common.Odometry
import org.mechdancer.networksInfo
import org.mechdancer.remote.presets.remoteHub
import kotlin.math.PI

fun main() = runBlocking<Unit>(Dispatchers.Default) {
    val remote = remoteHub("测试雷达组").apply {
        openAllNetworks()
        println(networksInfo())
    }
    startFaselaseLidarSet {
        lidar("/dev/pos3") {
            tag = "FrontLidar"
            pose = Odometry.odometry(.113, 0, PI / 2)
            inverse = false
        }
        lidar("/dev/pos4") {
            tag = "BackLidar"
            pose = Odometry.odometry(-.138, 0, PI / 2)
            inverse = false
        }
        painter = remote
    }
}
