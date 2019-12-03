package cn.autolabor.serialport.manager

import cn.autolabor.pm1.SerialPortChassis
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.runBlocking
import org.mechdancer.channel
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.geometry.angle.toDegree
import org.mechdancer.geometry.angle.toRad

fun main() {
    val manager = SerialPortManager()
    val robotOnOdometry = channel<Stamped<Odometry>>()
    manager.register(
        SerialPortChassis(
            GlobalScope,
            robotOnOdometry,

            4 * 400 * 20,
            16384,

            .465,
            .105,
            .105,
            .355,

            40L,
            10.toRad(),
            1.1,
            90.toDegree(),
            45.toDegree(),
            1.1
        ))
    manager.sync()
    manager.waitingDevices().joinToString(", ").let(::println)
    runBlocking {
        for (pose in robotOnOdometry)
            println(pose)
    }
}
