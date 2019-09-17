package org.mechdancer.modules

import cn.autolabor.Odometry
import cn.autolabor.Stamped
import cn.autolabor.pathfollower.Circle
import cn.autolabor.pathfollower.VirtualLightSensor
import cn.autolabor.pathfollower.VirtualLightSensorPathFollower
import cn.autolabor.pathfollower.VirtualLightSensorPathFollower.FollowCommand.*
import cn.autolabor.pathmaneger.PathManager
import cn.autolabor.pm1.sdk.PM1
import cn.autolabor.transform.Transformation
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.mechdancer.algebra.function.vector.minus
import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.console.parser.buildParser
import org.mechdancer.console.parser.display
import org.mechdancer.console.parser.feedback
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.modules.Mode.Idle
import org.mechdancer.modules.Mode.Record
import org.mechdancer.modules.devices.Default
import org.mechdancer.modules.devices.Twist
import org.mechdancer.paintFrame2
import org.mechdancer.paintVectors
import org.mechdancer.remote.presets.RemoteHub
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sign

// 任务类型/工作状态
private enum class Mode {
    Record,
    Follow,
    Idle
}

/**
 * 循径模块
 *
 * 包含任务控制、路径管理、控制台解析、循径任务；
 * PM1 驱动启动后才能正常运行；
 */
fun CoroutineScope.startPathFollower(
    robotOnMap: ReceiveChannel<Stamped<Odometry>>,
    twistCommand: SendChannel<Twist>,
    remote: RemoteHub? = Default.remote
) {
    val file = File("path.txt")
    val path = PathManager(interval = 0.05)
    val follower =
        VirtualLightSensorPathFollower(
            VirtualLightSensor(
                -Transformation.fromPose(vector2DOf(0.15, 0.0), 0.toRad()),
                Circle(radius = 0.2, vertexCount = 64)))

    var mode = Idle
    var enabled = false
    val parser = buildParser {
        this["record"] = {
            when (mode) {
                Record      -> "Recording"
                Mode.Follow -> "Is following now"
                Idle        -> {
                    mode = Record
                    launch {
                        while (isActive && mode == Record) {
                            val (_, current) = robotOnMap.receive()
                            if (path.record(current.p))
                                remote?.paintVectors("path", path.get())
                        }
                    }
                    "Recording"
                }
            }
        }
        this["pause"] = {
            when (mode) {
                Record      -> {
                    mode = Idle
                    "Paused"
                }
                Mode.Follow -> "Is following now"
                Idle        -> "..."
            }
        }
        this["clear"] = {
            mode = Idle
            path.clear()
            remote?.paintFrame2("path", emptyList())
            "path cleared"
        }
        this["show"] = {
            remote?.paintVectors("path", path.get())
            path.get().let { "path count = ${it.size}\n${it.joinToString("\n") { p -> "${p.x}\t${p.y}" }}" }
        }

        this["save"] = { path.saveTo(file); "${path.size} nodes saved" }
        this["load"] = {
            mode = Idle
            path.loadFrom(file)
            remote?.paintVectors("path", path.get())
            "${path.size} nodes loaded"
        }
        this["delete"] = { file.writeText(""); "path save deleted" }

        this["go"] = {
            when (mode) {
                Record      -> "Is Recording now."
                Mode.Follow -> "Ok."
                Idle        -> {
                    mode = Mode.Follow
                    follower.path = path.get()
                    launch {
                        while (isActive && mode == Mode.Follow) {
                            robotOnMap.receive().data
                                .toTransformation()
                                .let { follower(-it) }
                                .let { command ->
                                    when (command) {
                                        is Follow -> {
                                            val (v, w) = command
                                            twistCommand.send(Twist(v, w))
                                        }
                                        else      -> {
                                            println(command)
                                            when (command) {
                                                is Turn   -> {
                                                    val (angle) = command
                                                    println("turn $angle rad")
                                                    twistCommand.send(Twist(.0, .0))
                                                    delay(200L)
                                                    val (p0, d0) = robotOnMap.receive().data
                                                    // 前进 2.5cm 补不足
                                                    while (true) {
                                                        twistCommand.send(Twist(.1, .0))
                                                        val (p, _) = robotOnMap.receive().data
                                                        if ((p - p0).norm() > 0.025) break
                                                    }
                                                    // 旋转
                                                    val w = angle.sign * PI / 10
                                                    val delta = abs(angle)
                                                    while (true) {
                                                        twistCommand.send(Twist(.0, w))
                                                        val (_, d) = robotOnMap.receive().data
                                                        if (abs(d.asRadian() - d0.asRadian()) > delta) break
                                                    }
                                                }
                                                is Error,
                                                is Finish -> mode = Idle
                                            }
                                        }
                                    }
                                }
                            remote?.run {
                                val shape = follower.sensor.areaShape
                                paintVectors("sensor", shape + shape.first())
                            }
                            if (mode != Mode.Follow) {
                                enabled = false
                                PM1.runCatching { setCommandEnabled(false) }
                            }
                        }
                    }
                    "Ok."
                }
            }
        }
        this["\'"] = {
            enabled = !enabled
            PM1.runCatching { setCommandEnabled(enabled) }
            if (enabled) "!" else "?"
        }

        this["state"] = { mode }
        this["shutdown"] = { cancel(); "Bye~" }
    }

    /** 从控制台阻塞解析 */
    launch {
        while (isActive)
            readLine()
                ?.let(parser::invoke)
                ?.map(::feedback)
                ?.forEach(::display)
    }
}
