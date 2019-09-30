package org.mechdancer.simulation

import cn.autolabor.locator.ParticleFilterBuilder.Companion.particleFilter
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
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity.Companion.velocity
import org.mechdancer.common.filters.Differential
import org.mechdancer.common.toPose
import org.mechdancer.common.toTransformation
import org.mechdancer.modules.Default.remote
import org.mechdancer.modules.channel
import org.mechdancer.modules.startLocationFilter
import org.mechdancer.paint
import org.mechdancer.paintPose
import org.mechdancer.simulation.DifferentialOdometry.Key.Left
import org.mechdancer.simulation.DifferentialOdometry.Key.Right
import org.mechdancer.simulation.random.Normal
import org.mechdancer.struct.StructBuilderDSL.Companion.struct
import kotlin.random.Random

// 起始时刻
private const val T0 = 0L
// 仿真速度
private const val SPEED = 1
// 仿真运行频率
private const val FREQUENCY = 50L
// 定位频率
private const val LOCATE_FREQUENCY = 7.0
// 定位命中率
private const val LOCATE_RATE = LOCATE_FREQUENCY / FREQUENCY
// 定位标准差
private const val LOCATE_SIGMA = 1E-4
// 定位标签
private const val BEACON_TAG = "定位标签"
// 标签位置
private const val BEACON_OFFSET = -.31
// 里程计采样率
private const val ODOMETRY_FREQUENCY = 20L
// 里程计周期
private const val ODOMETRY_PERIOD = 1000L / ODOMETRY_FREQUENCY
// 机器人机械结构
private val robot = struct(Chassis(Stamped(T0, Odometry()))) {
    Encoder(Left) asSub { pose(0, +0.205) }
    Encoder(Right) asSub { pose(0, -0.200) }
    BEACON_TAG asSub { pose(BEACON_OFFSET, 0) }
}
// 编码器在机器人上的位姿
private val encodersOnRobot =
    robot.devices
        .mapNotNull { (device, tf) -> (device as? Encoder)?.to(tf.toPose()) }
        .toMap()
// 定位标签在机器人上的位姿
private val beaconOnRobot =
    robot.devices[BEACON_TAG]!!.toPose().p

// 定位误差
private fun locateError(p: Vector2D) =
    p + vector2DOf(
        Normal.next(.0, LOCATE_SIGMA),
        Normal.next(.0, LOCATE_SIGMA))

// 差动里程计
private val odometry = DifferentialOdometry(0.4, Stamped(T0, Odometry()))
// 仿真
private val random = newRandomDriving().let { if (SPEED > 0) it power SPEED else it }

// 差动里程计仿真实验
@ExperimentalCoroutinesApi
fun main() = runBlocking {
    // 里程计采样计数
    var odometryTimes = 0L
    // 位姿增量计算
    val differential = Differential(robot.what.get(), T0) { _, old, new -> new minusState old }
    // 话题
    val robotOnOdometry = channel<Stamped<Odometry>>()
    val beaconOnMap = channel<Stamped<Vector2D>>()
    val robotOnMap = channel<Stamped<Odometry>>()
    // 任务
    startLocationFilter(
        robotOnOdometry = robotOnOdometry,
        beaconOnMap = beaconOnMap,
        robotOnMap = robotOnMap,
        filter = particleFilter {
            beaconOnRobot = vector2DOf(BEACON_OFFSET, 0)
        })
    var actual = robot.what.odometry
    launch {
        for ((t, pose) in robotOnMap) {
            remote.paintPose("滤波", pose)
            if (t == actual.time)
                displayOnConsole(
                    "时间" to t / 1000.0,
                    "误差" to (actual.data.p - pose.p).norm())
        }
    }
    // 运行仿真
    speedSimulation(this, T0, 1000L / FREQUENCY, SPEED) { t ->
        if (t < 10000) velocity(0, .5) else random.next()
    }.consumeEach { (t, v) ->
        //  计算机器人位姿增量
        actual = robot.what.drive(v, t)
        val delta = differential.update(actual.data, t).data
        // 计算编码器增量
        for ((encoder, p) in encodersOnRobot) encoder.update(p, delta)
        // 计算里程计
        val get = { key: DifferentialOdometry.Key -> encodersOnRobot.keys.single { (k, _) -> k == key }.value }
        val pose = odometry.update(get(Left) to get(Right), t).data
        // 定位采样
        if (Random.nextDouble() < LOCATE_RATE)
            actual.data
                .toTransformation()(beaconOnRobot)
                .to2D()
                .let(::locateError)
                .also { beacon ->
                    remote.paint(BEACON_TAG, beacon.x, beacon.y)
                    beaconOnMap.send(Stamped(t, beacon))
                }
        // 里程计采样
        if (t > odometryTimes * ODOMETRY_PERIOD) {
            ++odometryTimes
            robotOnOdometry.send(Stamped(t, pose))
        }
        // 显示
        remote.paintPose("实际", actual.data)
    }
}
