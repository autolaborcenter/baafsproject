package org.mechdancer.modules

import cn.autolabor.BehaviorTree.Behavior.Action
import cn.autolabor.BehaviorTree.Behavior.Waiting
import cn.autolabor.BehaviorTree.Logic.*
import cn.autolabor.BehaviorTree.Result.*
import cn.autolabor.Odometry
import cn.autolabor.Temporary
import cn.autolabor.Temporary.Operation.DELETE
import cn.autolabor.pathfollower.Circle
import cn.autolabor.pathfollower.VirtualLightSensor
import cn.autolabor.pathfollower.VirtualLightSensorPathFollower
import cn.autolabor.pathfollower.VirtualLightSensorPathFollower.FollowCommand
import cn.autolabor.pathfollower.VirtualLightSensorPathFollower.FollowCommand.*
import cn.autolabor.pathmaneger.PathManager
import cn.autolabor.pm1.sdk.PM1
import cn.autolabor.transform.TransformSystem
import cn.autolabor.transform.Transformation
import org.mechdancer.algebra.function.vector.minus
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.console.parser.buildParser
import org.mechdancer.console.parser.display
import org.mechdancer.console.parser.feedback
import org.mechdancer.geometry.angle.toAngle
import org.mechdancer.geometry.angle.toDegree
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.modules.Coordination.Map
import org.mechdancer.modules.Coordination.Robot
import org.mechdancer.modules.PathFollowerModule.Mode.Idle
import org.mechdancer.modules.PathFollowerModule.Mode.Record
import org.mechdancer.paintFrame2
import org.mechdancer.paintVectors
import org.mechdancer.remote.presets.RemoteHub
import java.io.Closeable
import java.io.File
import kotlin.concurrent.thread

/**
 * 循径模块
 *
 * 包含任务控制、路径管理、控制台解析、循径任务；
 * PM1 驱动启动后才能正常运行；
 */
class PathFollowerModule(
    private val remote: RemoteHub? = Default.remote,
    private val system: TransformSystem<Coordination> = Default.system,
    private val control: (Double, Double) -> Unit
) : Closeable {
    // 任务类型/工作状态
    private enum class Mode {
        Record,
        Follow,
        Idle
    }

    init {
        system[Robot to Map] = Transformation.unit(2)
    }

    private val file = File("path.txt")
    private val path = PathManager(interval = 0.05)
    private val follower =
        VirtualLightSensorPathFollower(
            VirtualLightSensor(
                -Transformation.fromPose(vector2DOf(0.15, 0.0), 0.toRad()),
                Circle(radius = 0.2, vertexCount = 64)))
    private var command: FollowCommand = Follow(.0, .0)
    private val behaviors = LoopEach(
        Action {
            command = follower(system[Map to Robot]!!.transformation)
            when (command) {
                is Follow -> Success
                is Turn   -> Success
                Error     -> {
                    println("error")
                    Failure
                }
                Finish    -> {
                    println("finish")
                    Failure
                }
            }
        },
        First(
            Action {
                val temp = command
                if (temp is Follow) {
                    val (v, w) = temp
                    control(v, w)
                    Success
                } else
                    Failure
            },
            Sequence(
                Action {
                    control(.0, .0)
                    Success
                },
                Waiting(200L),
                Action {
                    control(.05, .0)
                    Running
                },
                Action {
                    control(.0, 10.0.toDegree().asRadian())
                    Running
                })))

    @Temporary(DELETE)
    var offset = Odometry()

    private var mode = Idle
    private var enabled = false
    private var running = true
    private val parser = buildParser {
        this["record"] = {
            when (mode) {
                Record      -> "Recording"
                Mode.Follow -> "Is following now"
                Idle        -> {
                    mode = Record
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
                    mode = Mode.Follow
                    follower.path = path.get()
                    thread {
                        while (mode == Mode.Follow) {
                            follow()
                            Thread.sleep(100)
                        }
                    }
                    "Ok."
                }
            }
        }
        this["\'"] = {
            enabled = !enabled
            PM1.setCommandEnabled(enabled)
            if (enabled) "!" else "?"
        }

        this["state"] = { mode }
        this["shutdown"] = { running = false; "Bye~" }
    }

    /** 记录路径点 */
    fun record(p: Vector2D) {
        if (mode == Record && path.record(p))
            remote?.paintVectors("path", path.get())
    }

    /** 从控制台阻塞解析 */
    fun parseRepeatedly() {
        while (running)
            readLine()
                ?.let(parser::invoke)
                ?.map(::feedback)
                ?.forEach(::display)
    }

    /** 解析一个脚本 */
    fun parse(cmd: String) =
        cmd.let(parser::invoke)
            .map(::feedback)

    private fun follow() {
        when (behaviors()) {
            Success -> TODO()
            Failure -> mode = Idle
            Running -> Unit
        }
        remote?.run {
            val shape = follower.sensor.areaShape
            paintVectors("sensor", shape + shape.first())
        }
        if (mode != Mode.Follow) {
            enabled = false
            PM1.setCommandEnabled(false)
        }
    }

    override fun close() {
        running = false
    }
}
