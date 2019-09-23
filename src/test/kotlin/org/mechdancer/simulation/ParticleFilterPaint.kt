package org.mechdancer.simulation

import cn.autolabor.locator.ParticleFilterBuilder.Companion.particleFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.mechdancer.algebra.function.vector.minus
import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.filters.Differential
import org.mechdancer.common.toPose
import org.mechdancer.common.toTransformation
import org.mechdancer.modules.devices.Default.paintWith
import org.mechdancer.modules.devices.Default.remote
import org.mechdancer.paint
import org.mechdancer.simulation.DifferentialOdometry.Key.Left
import org.mechdancer.simulation.DifferentialOdometry.Key.Right
import org.mechdancer.struct.StructBuilderDSL.Companion.struct
import kotlin.random.Random

private const val BEACON = "定位标签"
private const val BEACON_OFFSET = -0.1

// 差动里程计仿真实验
@ExperimentalCoroutinesApi
fun main() = runBlocking {
    // 起始时刻
    val t0 = 0L
    // 机器人机械结构
    val robot = struct(Chassis(Stamped(t0, Odometry()))) {
        Encoder(Left) asSub { pose(0, +0.202) }
        Encoder(Right) asSub { pose(0, -0.2) }
        BEACON asSub { pose(BEACON_OFFSET, 0) }
    }
    // 编码器在机器人上的位姿
    val encodersOnRobot =
        robot.devices
            .mapNotNull { (device, tf) -> (device as? Encoder)?.to(tf.toPose()) }
            .toMap()
    // 定位标签在机器人上的位姿
    val beaconOnRobot =
        robot.devices[BEACON]!!.toPose().p
    // 里程计增量计算
    val differential = Differential(robot.what.get(), t0) { _, old, new -> new minusState old }
    // 差动里程计
    val odometry = DifferentialOdometry(0.4, Stamped(t0, Odometry()))
    // 粒子滤波
    val particleFilter = particleFilter {
        count = 128
        locatorOnRobot = vector2DOf(BEACON_OFFSET, 0)
    }.apply { paintWith(remote) }
    // 仿真
    val random = newNonOmniRandomDriving()
    produce {
        // 仿真时间
        var time = t0
        while (true) {
            time += 20
            send(Stamped(time, random.next()))
            delay(20)
        }
    }.consumeEach { (t, v) ->
        //  计算机器人位姿增量
        val actual = robot.what.drive(v, t).data
        val delta = differential.update(actual, t).data
        // 计算编码器增量
        for ((encoder, p) in encodersOnRobot) encoder.update(p, delta)
        // 计算里程计
        val get = { key: DifferentialOdometry.Key -> encodersOnRobot.keys.single { (k, _) -> k == key }.value }
        val pose = odometry.update(get(Left) to get(Right), t).data
        // 计算定位标签
        val beacon = actual.toTransformation()(beaconOnRobot).to2D()
        // 显示
        remote.paintPose("机器人", actual)
        remote.paintPose("里程计", pose)
        remote.paint(BEACON, beacon.x, beacon.y)

        // 滤波
        if (Random.nextDouble() < .4)
            particleFilter.measureHelper(Stamped(t, beacon))
        particleFilter.measureMaster(Stamped(t, pose))
            ?.data
            ?.also { result ->
                println("time = ${t / 1000.0}, error = ${(result.p - actual.p).norm()}")
                remote.paintPose("滤波", result)
            }
    }
}
