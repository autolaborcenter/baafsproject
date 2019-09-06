package org.mechdancer.modules

import cn.autolabor.pathfollower.Circle
import cn.autolabor.pathfollower.VirtualLightSensor
import cn.autolabor.pathfollower.VirtualLightSensorPathFollower
import cn.autolabor.pathfollower.VirtualLightSensorPathFollower.FollowCommand.*
import cn.autolabor.pathmaneger.loadTo
import cn.autolabor.pathmaneger.saveTo
import cn.autolabor.pm1.sdk.PM1
import cn.autolabor.transform.TransformSystem
import cn.autolabor.transform.Transformation
import org.mechdancer.algebra.function.vector.minus
import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.console.parser.buildParser
import org.mechdancer.console.parser.display
import org.mechdancer.console.parser.feedback
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.modules.PathFollowerModule.Coordination.BaseLink
import org.mechdancer.modules.PathFollowerModule.Coordination.Map
import org.mechdancer.modules.PathFollowerModule.Mode.Idle
import org.mechdancer.modules.PathFollowerModule.Mode.Record
import org.mechdancer.paintFrame2
import org.mechdancer.remote.presets.RemoteHub
import java.io.Closeable
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.sign

/**
 * 循径模块
 *
 * 包含任务控制、路径管理、控制台解析、循径任务；
 * PM1 驱动启动后才能正常运行；
 */
class PathFollowerModule(
    private val remote: RemoteHub,
    private val system: TransformSystem<Coordination>
) : Closeable {
    // 坐标系
    enum class Coordination {
        Map,
        BaseLink
    }

    // 任务类型
    enum class Mode {
        Record,
        Follow,
        Idle
    }

    init {
        system[BaseLink to Map] = Transformation.unit(2)
    }

    private val file = File("path.txt")
    private val path = mutableListOf<Vector2D>()
    private val follower =
        VirtualLightSensorPathFollower(
            VirtualLightSensor(
                -Transformation.fromPose(vector2DOf(0.15, 0.0), 0.toRad()),
                Circle(radius = 0.2, vertexCount = 64)))

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
            remote.paintFrame2("path", emptyList())
            "path cleared"
        }
        this["show"] = {
            buildString {
                appendln("path count = ${path.size}")
                for (node in path) appendln("${node.x}\t${node.y}")
            }
        }

        this["save"] = { file saveTo path; "${path.size} nodes saved" }
        this["load"] = {
            mode = Idle
            file loadTo path
            remote.paintFrame2("path", path.map { it.x to it.y })
            "${path.size} nodes loaded"
        }
        this["delete"] = { file.writeText(""); "path save deleted" }

        this["go"] = {
            when (mode) {
                Record      -> "Is Recording now."
                Mode.Follow -> "Ok."
                Idle        -> {
                    mode = Mode.Follow
                    follower.path = path
                    thread { while (mode == Mode.Follow) follow() }
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
    fun recordNode(p: Vector2D) {
        if (mode == Record && path.lastOrNull()?.let { (it - p).norm() > 0.05 } != false) {
            path += p
            remote.paintFrame2("path", path.map { it.x to it.y })
        }
    }

    /** 阻塞解析 */
    fun parseRepeatedly() {
        while (running)
            readLine()
                ?.let(parser::invoke)
                ?.map(::feedback)
                ?.forEach(::display)
    }

    private fun follow() {
        when (val command = follower(system[Map to BaseLink]!!.transformation)) {
            is Follow -> {
                val (v, w) = command
                PM1.drive(v, w)
            }
            is Turn   -> {
                val (angle) = command
                println("turn: $angle")
                PM1.drive(.0, .0)
                Thread.sleep(200)
                PM1.driveSpatial(.050, .0, .025, .0)
                PM1.driveSpatial(.0, angle.sign * .5, .0, abs(angle))
            }
            Error     -> {
                println("error")
                mode = Idle
            }
            Finish    -> {
                println("finish")
                mode = Idle
            }
        }
        follower
            .sensor
            .areaShape
            .map { it.x to it.y }
            .let { it + it.first() }
            .let { remote.paintFrame2("sensor", it) }
        if (mode != Idle) {
            enabled = false
            PM1.setCommandEnabled(false)
        }
        Thread.sleep(100)
    }

    override fun close() {
        running = false
    }
}
