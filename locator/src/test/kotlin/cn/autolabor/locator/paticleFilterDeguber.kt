package cn.autolabor.locator

import cn.autolabor.locator.ParticleFilterDebugerBuilderDsl.Companion.debugParticleFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.mechdancer.algebra.function.vector.euclid
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.networksInfo
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.simulation.displayOnConsole

@ExperimentalCoroutinesApi
fun main() = debugParticleFilter {
    // 仿真配置
    speed = 1
    frequency = 50L
    // 里程计配置
    odometryFrequency = 20.0
    leftWheel = vector2DOf(0, +.202)
    rightWheel = vector2DOf(0, -.2)
    wheelsWidthMeasure = 0.4
    // 定位配置
    beaconFrequency = 4.0
    beaconSigma = 5E-3
    beaconDelay = 170L
    beacon = vector2DOf(-.05, 0)
    // 定位异常配置
    pBeaconError = .05
    pBeaconRecover = .8
    beaconErrorRange = 1.5
    // 滤波器配置
    particleFilter {
        beaconOnRobot = vector2DOf(-.05, 0)
        maxInconsistency = .05
        beaconWeight = .15 * count
    }
    // 数据分析
    analyze { t, actual, odometry ->
        displayOnConsole(
            "时间" to t / 1000.0,
            "误差" to (actual.p euclid odometry.p))
    }
    // 绘图
    painter = remoteHub("调试粒子滤波器").apply {
        openAllNetworks()
        println(networksInfo())
    }
}
