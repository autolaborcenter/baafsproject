package org.mechdancer.modules

import cn.autolabor.Odometry
import cn.autolabor.Stamped
import cn.autolabor.Temporary
import cn.autolabor.Temporary.Operation.DELETE
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
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.console.parser.buildParser
import org.mechdancer.console.parser.display
import org.mechdancer.console.parser.feedback
import org.mechdancer.geometry.angle.toAngle
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.modules.PathFollowerModule.Mode.Idle
import org.mechdancer.modules.PathFollowerModule.Mode.Record
import org.mechdancer.modules.devices.Default
import org.mechdancer.modules.devices.Twist
import org.mechdancer.paintFrame2
import org.mechdancer.paintVectors
import org.mechdancer.remote.presets.RemoteHub
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sign

/**
 * 循径模块
 *
 * 包含任务控制、路径管理、控制台解析、循径任务；
 * PM1 驱动启动后才能正常运行；
 */
class PathFollowerModule(
    private val scope: CoroutineScope,
    private val robotOnMap: ReceiveChannel<Stamped<Odometry>>,
    private val twistChannel: SendChannel<Twist>,
    private val remote: RemoteHub? = Default.remote
) {
    // 任务类型/工作状态
    private enum class Mode {
        Record,
        Follow,
        Idle
    }

    private val file = File("path.txt")
    private val path = PathManager(interval = 0.05)
    private val follower =
        VirtualLightSensorPathFollower(
            VirtualLightSensor(
                -Transformation.fromPose(vector2DOf(0.15, 0.0), 0.toRad()),
                Circle(radius = 0.2, vertexCount = 64)))

    @Temporary(DELETE)
    var offset = Odometry()

    private var mode = Idle
    private var enabled = false
    private val parser = buildParser {
        this["record"] = {
            when (mode) {
                Record      -> "Recording"
                Mode.Follow -> "Is following now"
                Idle        -> {
                    startRecord()
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

        @Temporary(DELETE)
        this["move"] = {
            val list = path.get().take(2)
            val p = list[0].to2D()
            val d = (list[1] - p).toAngle()
            offset = Odometry(p, d)
            "saved to offset"
        }

        this["go"] = {
            when (mode) {
                Record      -> "Is Recording now."
                Mode.Follow -> "Ok."
                Idle        -> {
                    startFollow()
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
        this["shutdown"] = { scope.cancel(); "Bye~" }
    }

    /** 从控制台阻塞解析 */
    fun parseRepeatedly() {
        scope.launch {
            while (isActive)
                readLine()
                    ?.let(parser::invoke)
                    ?.map(::feedback)
                    ?.forEach(::display)
        }
    }

    /** 解析一个脚本 */
    fun parse(cmd: String) =
        cmd.let(parser::invoke)
            .map(::feedback)

    private fun startRecord() {
        mode = Record
        scope.launch {
            while (isActive && mode == Record) {
                val (_, current) = robotOnMap.receive()
                if (path.record(current.p))
                    remote?.paintVectors("path", path.get())
            }
        }
    }

    private fun startFollow() {
        mode = Mode.Follow
        follower.path = path.get()
        scope.launch {
            while (isActive && mode == Mode.Follow) {
                robotOnMap.receive().data
                    .toTransformation()
                    .let { follower(-it) }
                    .let { command ->
                        when (command) {
                            is Follow -> {
                                val (v, w) = command
                                twistChannel.send(Twist(v, w))
                            }
                            else      -> {
                                println(command)
                                when (command) {
                                    is Turn   -> {
                                        val (angle) = command
                                        println("turn $angle rad")
                                        twistChannel.send(Twist(.0, .0))
                                        delay(200L)
                                        val (p0, d0) = robotOnMap.receive().data
                                        // 前进 2.5cm 补不足
                                        while (true) {
                                            twistChannel.send(Twist(.1, .0))
                                            val (p, _) = robotOnMap.receive().data
                                            if ((p - p0).norm() > 0.025) break
                                        }
                                        // 旋转
                                        val w = angle.sign * PI / 10
                                        val delta = abs(angle)
                                        while (true) {
                                            twistChannel.send(Twist(.0, w))
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
    }
}
