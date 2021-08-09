package com.faselase

import cn.autolabor.serialport.manager.SerialPortManager
import com.faselase.FaselaseLidarSetBuilderDsl.Companion.registerFaselaseLidarSet
import kotlinx.coroutines.*
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.channel
import org.mechdancer.common.shape.Circle
import org.mechdancer.common.shape.Polygon
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.networksInfo
import org.mechdancer.paint
import org.mechdancer.paintVectors
import org.mechdancer.remote.presets.remoteHub

@ObsoleteCoroutinesApi
fun main() {
    val remote = remoteHub("测试雷达组").apply {
        openAllNetworks()
        println(networksInfo())
    }
    val blind = Polygon(
        listOf(
            vector2DOf(+.0, +.0),
            vector2DOf(+.0, +.3),
            vector2DOf(+.3, +.3),
            vector2DOf(+.2, -.3)
        )
    )
    GlobalScope.launch {
        val `10cm` = Circle(.10).sample()
        val `15cm` = Circle(.15).sample()
        val `20cm` = Circle(.20).sample()
        while (true) {
            remote.paint("过滤区", blind)
            remote.paint("10cm", `10cm`)
            remote.paint("15cm", `15cm`)
            remote.paint("20cm", `20cm`)
            delay(2000L)
        }
    }
    val exceptions = channel<ExceptionMessage>()
    val manager = SerialPortManager(exceptions)
    val lidarSet =
        manager.registerFaselaseLidarSet(exceptions) {
            dataTimeout = 400L
            lidar {
                inverse = true
            }
            filter { p ->
                p !in blind
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
