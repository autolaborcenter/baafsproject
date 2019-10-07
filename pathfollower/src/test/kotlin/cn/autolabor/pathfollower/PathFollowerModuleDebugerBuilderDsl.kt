package cn.autolabor.pathfollower

import cn.autolabor.pathfollower.PathFollowerModuleBuilderDsl.Companion.startPathFollower
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import org.mechdancer.*
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.console.parser.Parser
import org.mechdancer.remote.modules.multicast.multicastListener
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.remote.protocol.SimpleInputStream
import org.mechdancer.simulation.Chassis
import org.mechdancer.struct.StructBuilderDSL
import java.io.DataInputStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** 粒子滤波测试用例构建 */
@BuilderDslMarker
class PathFollowerModuleDebugerBuilderDsl private constructor() {
    // 仿真配置
    var speed = 1
    var frequency = 50L
    // 里程计配置
    var odometryFrequency = 20.0
    // 跟踪器配置
    private var config: PathFollowerModuleBuilderDsl.() -> Unit = {}

    fun module(block: PathFollowerModuleBuilderDsl.() -> Unit) {
        config = block
    }

    companion object {
        private const val T0 = 0L

        @ExperimentalCoroutinesApi
        fun debugPathFollowerModule(block: PathFollowerModuleDebugerBuilderDsl.() -> Unit = {}) {
            PathFollowerModuleDebugerBuilderDsl()
                .apply(block)
                .run {
                    // 里程计周期
                    val odometryPeriod = 1000L / odometryFrequency
                    // 机器人机械结构
                    val robot = StructBuilderDSL.struct(Chassis(Stamped(T0, Odometry())))
                    // 里程计采样计数
                    var odometryTimes = 0L
                    // 位姿增量计算
                    val commands = channel<NonOmnidirectional>()

                    val remote = remoteHub("simulator") {
                        inAddition {
                            multicastListener { _, _, payload ->
                                if (payload.size == 16)
                                    GlobalScope.launch {
                                        val stream = DataInputStream(SimpleInputStream(payload))
                                        @Suppress("BlockingMethodInNonBlockingContext")
                                        commands.send(Velocity.velocity(stream.readDouble(), stream.readDouble()))
                                    }
                            }
                        }
                    }.apply {
                        openAllNetworks()
                        println(networksInfo())
                        thread(isDaemon = true) { while (true) invoke() }
                    }
                    // 话题
                    val robotOnMap = channel<Stamped<Odometry>>()
                    val commandToRobot = channel<NonOmnidirectional>()
                    val command = AtomicReference(Velocity.velocity(.0, .0))
                    val parser = Parser()
                    runBlocking {
                        // 任务
                        startPathFollower(
                            robotOnMap = robotOnMap,
                            commandOut = commandToRobot,
                            consoleParser = parser
                        ) {
                            painter = remote
                        }
                        launch { while (isActive) parser.parseFromConsole() }
                        launch { for ((v, w) in commands) command.set(Velocity.velocity(0.2 * v, 0.8 * w)) }
                        launch { for (v in commandToRobot) command.set(v) }
                        // 运行仿真
                        speedSimulation(this, T0, 1000L / frequency, speed) {
                            command.get()
                        }.consumeEach { (t, v) ->
                            //  计算机器人位姿增量
                            val actual = robot.what.drive(v, t)
                            // 里程计采样
                            if (t > odometryTimes * odometryPeriod) {
                                ++odometryTimes
                                robotOnMap.send(actual)
                            }
                            // 显示
                            remote.paintPose("实际", actual.data)
                        }
                    }
                }
        }
    }
}
