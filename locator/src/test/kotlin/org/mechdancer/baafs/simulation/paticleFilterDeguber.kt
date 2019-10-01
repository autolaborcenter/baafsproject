package org.mechdancer.baafs.simulation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.mechdancer.algebra.function.vector.minus
import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.algebra.implement.vector.vector2DOfZero
import org.mechdancer.baafs.simulation.ParticleFilterDebugerBuilderDsl.Companion.debugParticleFilter
import org.mechdancer.displayOnConsole

@ExperimentalCoroutinesApi
fun main() = debugParticleFilter {
    // 仿真配置
    speed = 1
    frequency = 50L
    // 里程计配置
    odometryFrequency = 20.0
    leftWheel = vector2DOf(0, +.205)
    rightWheel = vector2DOf(0, -.205)
    wheelsWidthMeasure = 0.4
    // 定位配置
    beaconFrequency = 7.0
    beaconSigma = 1E-3
    beacon = vector2DOf(-0.05, 0)
    // 滤波器配置
    particleFilter {
        beaconOnRobot = vector2DOfZero()
    }
    // 数据分析
    analyze { t, actual, odometry ->
        displayOnConsole(
            "时间" to t / 1000.0,
            "误差" to (actual.p - odometry.p).norm())
    }
}
