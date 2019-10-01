package org.mechdancer.baafs.simulation

import cn.autolabor.locator.ParticleFilterBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.mechdancer.algebra.function.vector.minus
import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.algebra.function.vector.plus
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.algebra.implement.vector.vector2DOfZero
import org.mechdancer.baafs.modules.Default
import org.mechdancer.baafs.modules.channel
import org.mechdancer.baafs.modules.startLocationFilter
import org.mechdancer.common.*
import org.mechdancer.common.filters.Differential
import org.mechdancer.paint
import org.mechdancer.paintPose
import org.mechdancer.simulation.Chassis
import org.mechdancer.simulation.DifferentialOdometry
import org.mechdancer.simulation.DifferentialOdometry.Key
import org.mechdancer.simulation.DifferentialOdometry.Key.Left
import org.mechdancer.simulation.DifferentialOdometry.Key.Right
import org.mechdancer.simulation.Encoder
import org.mechdancer.simulation.random.Normal
import org.mechdancer.struct.StructBuilderDSL
import kotlin.random.Random

class ParticleFilterDebugerBuilderDsl
private constructor() {
    // 仿真配置
    var speed = 1
    var frequency = 50L
    // 定位配置
    var beaconFrequency = 7.0
    var beaconSigma = 1E-3
    var beacon = vector2DOfZero()
    // 里程计采样率
    var odometryFrequency = 20.0
    var leftWheel = vector2DOf(0, +.2)
    var rightWheel = vector2DOf(0, -.2)
    var wheelsWidthMeasure = 0.4
    // 滤波器配置
    private var filterConfig: ParticleFilterBuilder.() -> Unit = {}
    // 数据分析
    private var analyzer: (t: Long, actual: Odometry, odometry: Odometry) -> Unit =
        { t, actual, odometry ->
            displayOnConsole(
                "时间" to t / 1000.0,
                "误差" to (actual.p - odometry.p).norm())
        }

    fun particleFilter(block: ParticleFilterBuilder.() -> Unit) {
        filterConfig = block
    }

    fun analyze(block: (Long, Odometry, Odometry) -> Unit) {
        analyzer = block
    }

    companion object {
        private const val T0 = 0L
        private const val BEACON_TAG = "定位标签"

        @ExperimentalCoroutinesApi
        fun debugParticleFilter(block: ParticleFilterDebugerBuilderDsl.() -> Unit = {}) {
            ParticleFilterDebugerBuilderDsl()
                .apply(block)
                .run {
                    // 定位命中率
                    val beaconRate = beaconFrequency / frequency
                    // 里程计周期
                    val odometryPeriod = 1000L / odometryFrequency
                    // 机器人机械结构
                    val robot = StructBuilderDSL.struct(Chassis(Stamped(T0,
                                                                        Odometry()))) {
                        Encoder(Left) asSub { pose = Odometry(leftWheel) }
                        Encoder(Right) asSub { pose = Odometry(rightWheel) }
                        BEACON_TAG asSub { pose = Odometry(beacon) }
                    }
                    // 编码器在机器人上的位姿
                    val encodersOnRobot =
                        robot.devices
                            .mapNotNull { (device, tf) -> (device as? Encoder)?.to(tf.toPose()) }
                            .toMap()
                    // 定位标签在机器人上的位姿
                    val beaconOnRobot =
                        robot.devices[BEACON_TAG]!!.toPose().p
                    // 差动里程计
                    val odometry = DifferentialOdometry(wheelsWidthMeasure,
                                                        Stamped(T0,
                                                                Odometry()))
                    // 仿真
                    val random = newRandomDriving().let { if (speed > 0) it power speed else it }
                    // 里程计采样计数
                    var odometryTimes = 0L
                    // 位姿增量计算
                    val differential = Differential(robot.what.get(),
                                                    T0) { _, old, new -> new minusState old }
                    // 话题
                    val robotOnOdometry = channel<Stamped<Odometry>>()
                    val beaconOnMap = channel<Stamped<Vector2D>>()
                    val robotOnMap = channel<Stamped<Odometry>>()
                    runBlocking {
                        // 任务
                        startLocationFilter(
                            robotOnOdometry = robotOnOdometry,
                            beaconOnMap = beaconOnMap,
                            robotOnMap = robotOnMap,
                            filter = ParticleFilterBuilder.particleFilter(filterConfig))
                        var actual = robot.what.odometry
                        launch {
                            // 在控制台打印误差
                            for ((t, pose) in robotOnMap) {
                                Default.remote.paintPose("滤波", pose)
                                if (t == actual.time) analyzer(t, actual.data, pose)
                            }
                        }
                        // 运行仿真
                        speedSimulation(this,
                                        T0,
                                        1000L / frequency,
                                        speed) { t ->
                            if (t < 10000) Velocity.velocity(0, .5) else random.next()
                        }.consumeEach { (t, v) ->
                            //  计算机器人位姿增量
                            actual = robot.what.drive(v, t)
                            val delta = differential.update(actual.data, t).data
                            // 计算编码器增量
                            for ((encoder, p) in encodersOnRobot) encoder.update(p, delta)
                            // 计算里程计
                            val get = { key: Key ->
                                encodersOnRobot.keys.single { (k, _) -> k == key }.value
                            }
                            val pose = odometry.update(get(Left) to get(Right), t).data
                            // 定位采样
                            if (Random.nextDouble() < beaconRate)
                                actual.data
                                    .toTransformation()(beaconOnRobot)
                                    .to2D()
                                    .let {
                                        it + vector2DOf(Normal.next(.0, beaconSigma),
                                                        Normal.next(.0, beaconSigma))
                                    }
                                    .also { beacon ->
                                        Default
                                            .remote.paint(BEACON_TAG, beacon.x, beacon.y)
                                        beaconOnMap.send(Stamped(t, beacon))
                                    }
                            // 里程计采样
                            if (t > odometryTimes * odometryPeriod) {
                                ++odometryTimes
                                robotOnOdometry.send(Stamped(t, pose))
                            }
                            // 显示
                            Default.remote.paintPose("实际", actual.data)
                        }
                    }
                }
        }
    }
}
