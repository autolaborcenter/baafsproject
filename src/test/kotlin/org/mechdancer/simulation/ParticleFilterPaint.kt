package org.mechdancer.simulation

import cn.autolabor.locator.ParticleFilterBuilder.Companion.particleFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.mechdancer.algebra.function.vector.x
import org.mechdancer.algebra.function.vector.y
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.common.toTransformation
import org.mechdancer.modules.devices.Default.remote
import org.mechdancer.paint
import org.mechdancer.simulation.prefabs.OneStepTransferRandomDrivingBuilderDSL.Companion.oneStepTransferRandomDriving
import org.mechdancer.simulation.random.Normal
import kotlin.random.Random

// 机器人机械结构
private val chassis = Chassis()
private val locatorOnRobot = vector2DOf(-0.31, 0)
private val filter = particleFilter {
    locatorOnRobot = vector2DOf(-0.31, 0)
}

@ExperimentalCoroutinesApi
fun main() = runBlocking {
    produce {
        // 随机行走
        oneStepTransferRandomDriving {
            vx(0.1) {
                row(0.99, 0.01, 0.00)
                row(0.00, 0.96, 0.04)
                row(0.00, 0.01, 0.99)
            }
            w(0.5) {
                row(0.90, 0.10, 0.00)
                row(0.02, 0.96, 0.02)
                row(0.00, 0.10, 0.90)
            }
        }.run {
            while (true) {
                send(next())
                delay(100L)
            }
        }
    }.consumeEach { v ->
        //  计算机器人位姿增量
        val data = chassis.drive(v)
        val (_, robotOnOdometry) = data
        val locatorOnOdometry = robotOnOdometry.toTransformation()(locatorOnRobot)

        robotOnOdometry.also { (p, d) ->
            remote.paint("chassis", p.x, p.y, d.asRadian())
            filter.measureMaster(data)
        }
        if (Random.nextDouble() > 0.5)
            locatorOnOdometry.run {
                remote.paint("locator",
                             x + Normal.next(.0, 0.02),
                             y + Normal.next(.0, 0.02))
            }
    }
}
