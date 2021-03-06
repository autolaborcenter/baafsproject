package cn.autolabor.pm1

import cn.autolabor.pm1.SerialPortChassisBuilderDsl.Companion.registerPM1Chassis
import cn.autolabor.serialport.manager.SerialPortManager
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.mechdancer.channel
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.exceptions.ExceptionMessage

@ObsoleteCoroutinesApi
fun main() {
    val robotOnOdometry = channel<Stamped<Odometry>>()
    val exceptions = channel<ExceptionMessage>()
    with(SerialPortManager(exceptions)) {
        registerPM1Chassis(robotOnOdometry)
        while (sync().isNotEmpty())
            Thread.sleep(100L)
    }
    runBlocking {
        for (pose in robotOnOdometry)
            println(pose)
    }
}
