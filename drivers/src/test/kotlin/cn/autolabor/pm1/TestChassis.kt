package cn.autolabor.pm1

import cn.autolabor.pm1.ChassisBuilderDsl.Companion.startPM1Chassis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.mechdancer.channel
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped

fun main() = runBlocking(Dispatchers.Default) {
    val robotOnOdometry = channel<Stamped<Odometry>>()
    startPM1Chassis(robotOnOdometry)
    for (pose in robotOnOdometry) println(pose)
}
