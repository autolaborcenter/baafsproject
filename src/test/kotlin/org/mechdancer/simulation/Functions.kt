package org.mechdancer.simulation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.paint
import org.mechdancer.remote.presets.RemoteHub
import org.mechdancer.simulation.prefabs.OneStepTransferRandomDrivingBuilderDSL
import java.io.DataOutputStream
import kotlin.system.measureTimeMillis

fun newNonOmniRandomDriving() =
    OneStepTransferRandomDrivingBuilderDSL.oneStepTransferRandomDriving {
        vx(0.1) {
            row(-1, .99, .00)
            row(.00, -1, .02)
            row(.00, .01, -1)
        }
        w(0.5) {
            row(-1, .01, .01)
            row(.01, -1, .01)
            row(.01, .01, -1)
        }
    }

// 倍速仿真
@ExperimentalCoroutinesApi
fun <T> speedSimulation(
    scope: CoroutineScope,
    t0: Long = 0,
    dt: Long = 5L,
    speed: Int = 1,
    block: (Long) -> T
) =
    when {
        speed > 0 -> scope.produce {
            // 仿真时间
            var time = t0
            while (true) {
                val cost = measureTimeMillis {
                    time += dt * speed
                    send(Stamped(time, block(time)))
                }
                if (dt > cost) delay(dt - cost)
            }
        }
        speed < 0 -> scope.produce {
            // 仿真时间
            var time = t0
            while (true) {
                time += dt
                send(Stamped(time, block(time)))
                delay(dt * -speed)
            }
        }
        else      -> throw IllegalArgumentException("speed cannot be zero")
    }

/**
 * 画位姿信号
 */
fun RemoteHub.paintPose(
    topic: String,
    pose: Odometry
) = paint(topic) {
    DataOutputStream(this).apply {
        writeByte(3)
        writeDouble(pose.p.x)
        writeDouble(pose.p.y)
        writeDouble(pose.d.asRadian())
    }
}
