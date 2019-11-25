package cn.autolabor.pm1

import cn.autolabor.pm1.model.ChassisStructure
import cn.autolabor.pm1.model.ControlVariable
import cn.autolabor.pm1.model.IncrementalEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.mechdancer.geometry.angle.toDegree
import org.mechdancer.geometry.angle.toRad

fun main() = runBlocking(Dispatchers.Default) {
    val chassis = Chassis(
            this,

            wheelsEncoder = IncrementalEncoder(4 * 400 * 20),
            rudderEncoder = IncrementalEncoder(16384),
            structure = ChassisStructure(.465, .105, .105, .355),

            odometryInterval = 40L,
            maxWheelSpeed = 10.toRad(),
            maxVelocity = ControlVariable.Velocity(1.1, 90.toDegree()),
            optimizeWidth = 45.toDegree(),

            retryInterval = 500L)
    while (true) {
        println(chassis.odometry)
        delay(200L)
    }
}
