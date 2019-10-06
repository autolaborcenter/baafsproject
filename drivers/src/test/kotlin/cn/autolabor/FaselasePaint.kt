package cn.autolabor

import com.faselase.Resource
import org.mechdancer.networksInfo
import org.mechdancer.paintVectors
import org.mechdancer.remote.presets.remoteHub
import kotlin.concurrent.thread

// 此测试可画出激光雷达数据

fun main() {
    val remote = remoteHub("simulator").apply {
        openAllNetworks()
        println(networksInfo())
        thread(isDaemon = true) { while (true) invoke() }
    }

    var time = System.currentTimeMillis()
    val faselase = Resource { list ->
        val now = System.currentTimeMillis()
        if (now - time > 20) {
            time = now
            remote.paintVectors("faselase", list.map { it.data.toVector2D() })
        }
    }
    while (true) faselase()
}
