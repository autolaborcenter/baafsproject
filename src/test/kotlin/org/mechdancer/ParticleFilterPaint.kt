package org.mechdancer

import cn.autolabor.Odometry
import cn.autolabor.Stamped
import cn.autolabor.locator.ParticleFilterBuilder
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf

fun main() = runBlocking<Unit> {
    // 粒子滤波器
    val filter = ParticleFilterBuilder.particleFilter { locatorOnRobot = vector2DOf(-0.31, 0) }
    // 消息通道
    val robotOnLocator = channel<Stamped<Vector2D>>()
    val robotOnOdometry = channel<Stamped<Odometry>>()
    val robotOnMap = channel<Stamped<Odometry>>()
    // 使用里程计数据
    launch {
        while (true)
            robotOnOdometry.receive()
                .let(filter::measureMaster)
                ?.also { robotOnMap.send(it) }
    }
    // 使用定位数据
    launch {
        while (true)
            robotOnLocator.receive()
                .let(filter::measureHelper)
    }
}
