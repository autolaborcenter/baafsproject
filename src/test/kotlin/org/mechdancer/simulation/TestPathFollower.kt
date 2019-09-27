package org.mechdancer.simulation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity.Companion.velocity
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.modules.Default.commands
import org.mechdancer.modules.Default.remote
import org.mechdancer.modules.await
import org.mechdancer.modules.channel
import org.mechdancer.modules.startPathFollower
import org.mechdancer.struct.StructBuilderDSL.Companion.struct
import java.math.BigDecimal
import java.text.DecimalFormat
import java.util.concurrent.atomic.AtomicReference

// 起始时刻
private const val T0 = 0L
// 仿真速度
private const val SPEED = 1
// 仿真运行频率
private const val FREQUENCY = 50L
// 里程计采样率
private const val ODOMETRY_FREQUENCY = 20L
// 里程计周期
private const val ODOMETRY_PERIOD = 1000L / ODOMETRY_FREQUENCY
// 机器人机械结构
private val robot = struct(Chassis(Stamped(T0, Odometry())))

// 显示格式
private val format = DecimalFormat("0.000")

private fun displayOnConsole(vararg entry: Pair<String, Number>) =
    entry.joinToString(" | ") { (key, value) ->
        when (value) {
            is Float, is Double, is BigDecimal -> "$key = ${format.format(value)}"
            else                               -> "$key = $value"
        }
    }.let(::println)

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
            commandOut = commandToRobot)
        launch { for ((v, w) in commands) command.set(velocity(0.2 * v, 0.5 * w)) }
        launch { for (v in commandToRobot) command.set(v) }
        // 运行仿真
        launch {
            speedSimulation(this, T0, 1000L / FREQUENCY, SPEED) {
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
        await()
    }
}
