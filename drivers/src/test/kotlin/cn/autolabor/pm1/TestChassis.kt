package cn.autolabor.pm1

import cn.autolabor.pm1.SerialPortChassisBuilderDsl.Companion.registerPM1Chassis
import cn.autolabor.serialport.manager.SerialPortManager
import kotlinx.coroutines.runBlocking
import org.mechdancer.channel
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.geometry.angle.toDegree

fun main() {
    val robotOnOdometry = channel<Stamped<Odometry>>()
    with(SerialPortManager()) {
        registerPM1Chassis(robotOnOdometry) {
            odometryInterval = 40L
            maxW = 45.toDegree()
        }
        while (!sync());
    }
    runBlocking {
        for (pose in robotOnOdometry)
            println(pose)
    }
}
