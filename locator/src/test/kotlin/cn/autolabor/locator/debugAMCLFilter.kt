package cn.autolabor.locator

import cn.autolabor.locator.AMCLFilterDebugerBuilderDsl.Companion.debugAMCLFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.mechdancer.algebra.function.vector.euclid
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.geometry.transformation.pose2D
import org.mechdancer.networksInfo
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.simulation.displayOnConsole
import kotlin.math.PI

@ExperimentalCoroutinesApi
fun main() = debugAMCLFilter {
    // 仿真配置
    speed = 1
    frequency = 50L
    origin = pose2D()
    // 里程计配置
    odometryFrequency = 20.0
    leftWheel = vector2DOf(0, +.211)
    rightWheel = vector2DOf(0, -.201)
    wheelsWidthMeasure = 0.4
    // 定位配置
    beaconFrequency = 7.0
    beaconLossRate = 0.15 //.15
    beaconSigma = 5E-3 // 5E-3
    beaconDelay = 170L // 170L
    beaconOnRobot = vector2DOf(0, 0)
    // 定位异常配置
    beaconErrors {
        // 快速恢复的局外点
        error {
            pStart = .02
            pStop = .75
            range = .2
        }
        // 持续性的偏移
//        error {
//            pStart = .2
//            pStop = .2
//            range = .025
//        }
//      // 远且持久的移动(劫持)
//      error {
//          pStart = .01
//          pStop = .02
//          range = 1.0
//      }
    }
    // 滤波器配置
    AMCLFilter {
        initWaitNumber = 2
        minCount = 500
        maxCount = 2000
        tagPosition = vector2DOf(0.0, 0.0)
        dThresh = 0.1
        aThresh = 10 * PI / 180
        alpha1 = 0.2
        alpha2 = 0.2
        alpha3 = 0.2
        alpha4 = 0.2
        weightSigma = 0.1
    }
    // 数据分析
    analyze { t, actual, odometry ->
        displayOnConsole(
                "时间" to t / 1000.0,
                "误差" to (actual.p euclid odometry.p)
        )
    }
    // 绘图
    painter = remoteHub("调试粒子滤波器").apply {
        openAllNetworks()
        println(networksInfo())
    }
}
