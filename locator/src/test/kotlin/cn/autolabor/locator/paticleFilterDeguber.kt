package cn.autolabor.locator

import cn.autolabor.locator.ParticleFilterDebugerBuilderDsl.Companion.debugParticleFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.mechdancer.algebra.function.vector.minus
import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.dependency.must
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.remote.resources.MulticastSockets
import org.mechdancer.remote.resources.Networks
import org.mechdancer.simulation.displayOnConsole
import kotlin.concurrent.thread

@ExperimentalCoroutinesApi
fun main() = debugParticleFilter {
    // 仿真配置
    speed = 1
    frequency = 50L
    // 里程计配置
    odometryFrequency = 20.0
    leftWheel = vector2DOf(0, +.2)
    rightWheel = vector2DOf(0, -.2)
    wheelsWidthMeasure = 0.4
    // 定位配置
    beaconFrequency = 4.0
    beaconSigma = 1E-3
    beacon = vector2DOf(-.05, 0)
    // 滤波器配置
    particleFilter {
        beaconOnRobot = vector2DOf(-.05, 0)
        maxInconsistency = .05
    }
    // 数据分析
    analyze { t, actual, odometry ->
        displayOnConsole(
            "时间" to t / 1000.0,
            "误差" to (actual.p - odometry.p).norm())
    }
    // 绘图
    painter = remoteHub("调试粒子滤波器").apply {
        openAllNetworks()
        println("opened ${components.must<Networks>().view.size} networks on ${components.must<MulticastSockets>().address}")
        thread(isDaemon = true) { while (true) invoke() }
    }
}
