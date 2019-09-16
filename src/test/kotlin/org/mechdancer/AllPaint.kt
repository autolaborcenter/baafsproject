package org.mechdancer

import cn.autolabor.Odometry
import cn.autolabor.Stamped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.mechdancer.modules.PathFollowerModule
import org.mechdancer.modules.devices.Chassis.FrameworkRemoteChassis
import org.mechdancer.modules.devices.Default
import org.mechdancer.modules.devices.Locator.FrameworkRemoteLocator

fun main() {
    // 粒子滤波器
    val filter = Default.filter
    // 话题
    val scope = CoroutineScope(Dispatchers.Default)
    val locator = FrameworkRemoteLocator(scope)
    val chassis = FrameworkRemoteChassis(scope)
    val robotOnMap = Channel<Stamped<Odometry>>(Channel.CONFLATED)
    // 使用里程计数据
    scope.launch {
        while (true)
            chassis.robotPose.receive()
                .let(filter::measureMaster)
                ?.also { robotOnMap.send(it) }
    }
    // 使用定位数据
    scope.launch {
        while (true)
            locator.robotLocation.receive()
                .let(filter::measureHelper)
    }
    // 导航模块
    runBlocking {
        PathFollowerModule(
            robotOnMap = chassis.robotPose,
            twistChannel = chassis.twistCommand
        ).use { it.parseRepeatedly() }
    }
}

