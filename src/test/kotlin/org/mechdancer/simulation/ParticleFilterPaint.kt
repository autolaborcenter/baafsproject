package org.mechdancer.simulation

import cn.autolabor.locator.ParticleFilterBuilder.Companion.particleFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
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
import org.mechdancer.common.extension.clamp
import org.mechdancer.common.filters.Differential
import org.mechdancer.common.toPose
import org.mechdancer.common.toTransformation
import org.mechdancer.modules.devices.Default.paintWith
import org.mechdancer.modules.devices.Default.remote
import org.mechdancer.paint
import org.mechdancer.simulation.DifferentialOdometry.Key.Left
import org.mechdancer.simulation.DifferentialOdometry.Key.Right
import org.mechdancer.simulation.random.Normal
import org.mechdancer.struct.StructBuilderDSL.Companion.struct
import java.text.DecimalFormat
import kotlin.random.Random

// 起始时刻
private const val T0 = 0L
// 仿真速度
private const val SPEED = 1
// 定位频率
private const val FREQUENCY = 50L
// 定位频率
private const val LOCATE_FREQUENCY = 7L
// 定位标签
private const val BEACON_TAG = "定位标签"
// 标签位置
private const val BEACON_OFFSET = -.31
// 机器人机械结构
private val robot = struct(Chassis(Stamped(T0, Odometry()))) {
    Encoder(Left) asSub { pose(0, +0.2) }
    Encoder(Right) asSub { pose(0, -0.2) }
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

private fun randomDouble() =
    (-.05..+.05).clamp(Normal.next(.0, 1E-4))

// 定位误差
private fun locateError(p: Vector2D) =
    p + vector2DOf(randomDouble(), randomDouble())

// 里程计增量计算
private val differential = Differential(robot.what.get(), T0) { _, old, new -> new minusState old }
// 差动里程计
private val odometry = DifferentialOdometry(0.4, Stamped(T0, Odometry()))
// 粒子滤波
private val particleFilter = particleFilter {
    locatorOnRobot = vector2DOf(BEACON_OFFSET, 0)
    maxAge = 100
}.apply { paintWith(remote) }
// 仿真
val random = newNonOmniRandomDriving().let { if (SPEED > 0) it power SPEED else it }

// 差动里程计仿真实验
@ExperimentalCoroutinesApi
fun main() = runBlocking {
    var errorSum = .0
    var errorMemory = .0
    var i = 0L
    val format = DecimalFormat("0.000")
    speedSimulation(this, T0, 1000L / FREQUENCY, SPEED) { t ->
        if (t < 10000) velocity(0, .5) else random.next()
    }.consumeEach { (t, v) ->
        //  计算机器人位姿增量
        val actual = robot.what.drive(v, t).data
        val delta = differential.update(actual, t).data
        // 计算编码器增量
        for ((encoder, p) in encodersOnRobot) encoder.update(p, delta)
        // 计算里程计
        val get = { key: DifferentialOdometry.Key -> encodersOnRobot.keys.single { (k, _) -> k == key }.value }
        val pose = odometry.update(get(Left) to get(Right), t).data

        // 滤波
        if (Random.nextDouble() * FREQUENCY < LOCATE_FREQUENCY)
            actual.toTransformation()(beaconOnRobot).to2D()
                .let(::locateError)
                .also { beacon ->
                    remote.paint(BEACON_TAG, beacon.x, beacon.y)
                    particleFilter.measureHelper(Stamped(t, beacon))
                }
        // 显示
        remote.paintPose("机器人", actual)
        remote.paintPose("里程计", pose)
        particleFilter.measureMaster(Stamped(t, pose))
            ?.data
            ?.also { result ->
                val error = (result.p - actual.p).norm()
                remote.paintPose("滤波", result)
                buildString {
                    append("时刻 = ${format.format(t / 1000.0)}, ")

                    append("误差 = ${format.format(error)}, ")

                    if (t > 10000) {
                        errorSum += error
                        append("平均误差 = ${format.format(errorSum / ++i)}, ")
                    }

                    errorMemory = errorMemory * 0.9 + error * 0.1
                    append("近期平均误差 = ${format.format(errorMemory)}")
                }.let(::println)
            }
    }
}
