package org.mechdancer

import cn.autolabor.Odometry
import cn.autolabor.Stamped
import cn.autolabor.locator.ParticleFilterBuilder.Companion.particleFilter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.modules.PathFollowerModule
import org.mechdancer.modules.PathFollowerModule.Twist

fun <T> channel() = Channel<T>(Channel.CONFLATED)

fun main() = runBlocking {
    // 粒子滤波器
    val filter = particleFilter { locatorOnRobot = vector2DOf(-0.31, 0) }
    // 消息通道
    val robotOnLocator = channel<Stamped<Vector2D>>()
    val robotOnOdometry = channel<Stamped<Odometry>>()
    val robotOnMap = channel<Stamped<Odometry>>()
    val twistCommand = channel<Twist>()
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
    // 导航模块
    PathFollowerModule(
        robotOnMap = robotOnMap,
        twistChannel = twistCommand
    ).use { it.parseRepeatedly() }

    // launch pm1
    // PM1.initialize()
    // PM1.locked = false
    // PM1.setCommandEnabled(false)
    // GlobalScope.launch {
    //     while (true) {
    //         val (_, _, _, x, y, theta) = PM1.odometry
    //         odometryChannel.send(stamp(Odometry(vector2DOf(x, y), theta.toRad())))
    //         delay(40L)
    //     }
    // }
    // launch marvelmind
    // Resource { time, x, y ->
    //     GlobalScope.launch {
    //         locateChannel.send(Stamped(time, vector2DOf(x, y)))
    //     }
    // }.use {
    //     GlobalScope.launch {
    //         while (true) it()
    //     }
    // }
}

