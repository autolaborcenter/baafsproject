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

private const val dt = 5L

// 倍速仿真
@ExperimentalCoroutinesApi
fun <T> speedSimulation(
    scope: CoroutineScope,
    t0: Long = 0,
    speed: Int = 1,
    block: () -> T
) =
    scope.produce {
        // 仿真时间
        var time = t0
        while (true) {
            time += dt * speed
            send(Stamped(time, block()))
            delay(dt)
        }
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
