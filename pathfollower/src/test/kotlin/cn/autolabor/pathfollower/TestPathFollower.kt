package cn.autolabor.pathfollower

import cn.autolabor.pathfollower.PathFollowerModule.Companion.startPathFollower
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import org.mechdancer.channel
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity.Companion.velocity
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.dependency.must
import org.mechdancer.paintPose
import org.mechdancer.remote.modules.multicast.multicastListener
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.remote.protocol.SimpleInputStream
import org.mechdancer.remote.resources.MulticastSockets
import org.mechdancer.remote.resources.Networks
import org.mechdancer.simulation.Chassis
import org.mechdancer.speedSimulation
import org.mechdancer.struct.StructBuilderDSL.Companion.struct
import java.io.DataInputStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

// 起始时刻
private const val T0 = 0L
// 仿真速度
private const val SPEED = 10
// 仿真运行频率
private const val FREQUENCY = 50L
// 里程计采样率
private const val ODOMETRY_FREQUENCY = 20L
// 里程计周期
private const val ODOMETRY_PERIOD = 1000L / ODOMETRY_FREQUENCY
// 机器人机械结构
private val robot = struct(Chassis(Stamped(T0, Odometry()))) {}

private val commands_ = Channel<NonOmnidirectional>(Channel.CONFLATED)
val commands: ReceiveChannel<NonOmnidirectional> get() = commands_

val remote by lazy {
    remoteHub("simulator") {
        inAddition {
            multicastListener { _, _, payload ->
                if (payload.size == 16)
                    GlobalScope.launch {
                        val stream = DataInputStream(SimpleInputStream(payload))
                        @Suppress("BlockingMethodInNonBlockingContext")
                        commands_.send(velocity(stream.readDouble(), stream.readDouble()))
                    }
            }
        }
    }.apply {
        openAllNetworks()
        println("simulator open ${components.must<Networks>().view.size} networks on ${components.must<MulticastSockets>().address}")
        thread(isDaemon = true) { while (true) invoke() }
    }
}

// 差动里程计仿真实验
@ExperimentalCoroutinesApi
fun main() {
    // 里程计采样计数
    var odometryTimes = 0L
    // 指令缓存
    val command = AtomicReference(velocity(.0, .0))
    // 话题
    val robotOnMap = channel<Stamped<Odometry>>()
    val commandToRobot = channel<NonOmnidirectional>()
    with(CoroutineScope(Dispatchers.Default)) {
        // 任务
        startPathFollower(
            robotOnMap = robotOnMap,
            commandOut = commandToRobot,
            remote = remote)
        launch { for ((v, w) in commands) command.set(velocity(0.2 * v, 0.8 * w)) }
        launch { for (v in commandToRobot) command.set(v) }
        // 运行仿真
        runBlocking {
            speedSimulation(this,
                            T0,
                            1000L / FREQUENCY,
                            SPEED) {
                command.get()
            }.consumeEach { (t, v) ->
                //  计算机器人位姿增量
                val actual = robot.what.drive(v, t)
                // 里程计采样
                if (t > odometryTimes * ODOMETRY_PERIOD) {
                    ++odometryTimes
                    robotOnMap.send(actual)
                }
                // 显示
                remote.paintPose("实际", actual.data)
            }
        }
    }
}
