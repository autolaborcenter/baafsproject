package cn.autolabor.locator

import cn.autolabor.locator.ParticleFilterDebugerBuilderDsl.Companion.debugParticleFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.mechdancer.algebra.function.vector.euclid
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.geometry.transformation.pose2D
import org.mechdancer.networksInfo
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.simulation.displayOnConsole
import kotlin.math.PI

@ExperimentalCoroutinesApi
fun main() = debugParticleFilter {
    // 仿真配置
    speed = 2
    frequency = 50L
    origin = pose2D(3, 4, PI)
    // 里程计配置
    odometryFrequency = 20.0
    leftWheel = Vector2D(.0, +.21)
    rightWheel = Vector2D(.0, -.21)
    wheelsWidthMeasure = 0.4
    // 定位配置
    beaconFrequency = 7.0
    beaconLossRate = .15
    beaconSigma = 5E-3
    beaconDelay = 170L
    beaconOnRobot = Vector2D(-.05, .0)
    // 定位异常配置
    beaconErrors {
        // 快速恢复的局外点
        error {
            pStart = .05
            pStop = .75
            range = .3
        }
//      // 持续性的偏移
//      error {
//          pStart = .2
//          pStop = .2
//          range = .025
//      }
//      // 远且持久的移动(劫持)
//      error {
//          pStart = .01
//          pStop = .02
//          range = 1.0
//      }
    }
    // 滤波器配置
    particleFilter {
        beaconOnRobot = Vector2D(-.05, .0)
        maxInconsistency = .04
        convergence { (age, _, d) -> age > .2 && d > .9 }
        divergence { (age, _, _) -> age < .1 }
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
