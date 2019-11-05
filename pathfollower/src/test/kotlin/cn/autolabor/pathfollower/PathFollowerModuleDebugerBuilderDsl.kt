package cn.autolabor.pathfollower

import cn.autolabor.business.BusinessBuilderDsl
import cn.autolabor.business.BusinessBuilderDsl.Companion.business
import cn.autolabor.business.parseFromConsole
import cn.autolabor.business.registerBusinessParser
import kotlinx.coroutines.*
import org.mechdancer.*
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity.Companion.velocity
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.console.parser.buildParser
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.exceptions.ExceptionServer
import org.mechdancer.remote.modules.multicast.multicastListener
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.remote.protocol.SimpleInputStream
import org.mechdancer.simulation.Chassis
import org.mechdancer.simulation.speedSimulation
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
    private var config: BusinessBuilderDsl.() -> Unit = {}

    fun module(block: BusinessBuilderDsl.() -> Unit) {
        config = block
    }

    companion object {
        private const val T0 = 0L

        @ExperimentalCoroutinesApi
        fun debugPathFollowerModule(block: PathFollowerModuleDebugerBuilderDsl.() -> Unit = {}) {
            PathFollowerModuleDebugerBuilderDsl()
                .apply(block)
                .run {
                    val dt = 1000L / frequency
                    // 里程计周期
                    val odometryPeriod = 1000L / odometryFrequency
                    // 机器人机械结构
                    val robot = StructBuilderDSL.struct(Chassis(Stamped(T0, Odometry.pose())))
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
                                        commands.send(velocity(stream.readDouble(), stream.readDouble()))
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
                    val exceptions = channel<ExceptionMessage>()
                    val command = AtomicReference(velocity(.0, .0))
                    val exceptionServer = ExceptionServer()
                    val parser = buildParser {
                        this["exceptions"] = {
                            exceptionServer.get().joinToString("\n")
                        }
                    }
                    runBlocking {
                        // 任务
                        val business = business(
                            robotOnMap = robotOnMap,
                            robotOnOdometry = robotOnMap,
                            commandOut = commandToRobot,
                            exceptions = exceptions
                        ) {
                            this@run.config(this)
                            painter = remote
                        }
                        launch {
                            for (exception in exceptions)
                                exceptionServer.update(exception)
                        }
                        launch {
                            for ((v, w) in commands)
                                command.set(velocity(0.2 * v, 0.8 * w))
                        }
                        launch {
                            val watchDog = WatchDog(this, 3 * dt) { command.set(velocity(0, 0)) }
                            for (v in commandToRobot) {
                                if (!exceptionServer.isEmpty()) continue
                                watchDog.feed()
                                command.set(v)
                            }
                        }
                        launch {
                            registerBusinessParser(business, parser)
                            while (isActive) parser.parseFromConsole()
                        }
                        // 运行仿真
                        for ((t, v) in speedSimulation(T0, dt, speed) { command.get() }) {
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
