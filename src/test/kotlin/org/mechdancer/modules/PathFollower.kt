package org.mechdancer.modules

import cn.autolabor.pathfollower.Circle
import cn.autolabor.pathfollower.VirtualLightSensor
import cn.autolabor.pathfollower.VirtualLightSensorPathFollower
import cn.autolabor.pathfollower.VirtualLightSensorPathFollower.FollowCommand.*
import cn.autolabor.pathmaneger.PathManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.mechdancer.SimpleLogger
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity.Companion.velocity
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.console.parser.buildParser
import org.mechdancer.console.parser.display
import org.mechdancer.console.parser.feedback
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.geometry.transformation.Transformation
import org.mechdancer.modules.Mode.Idle
import org.mechdancer.modules.Mode.Record
import org.mechdancer.paintFrame3
import org.mechdancer.paintPoses
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
 */
@ExperimentalCoroutinesApi
fun CoroutineScope.startPathFollower(
    robotOnMap: ReceiveChannel<Stamped<Odometry>>,
    commandOut: SendChannel<NonOmnidirectional>,
    remote: RemoteHub? = Default.remote
) {
    val file = File("path.txt")
    val path = PathManager(interval = 0.05)
    val follower =
        VirtualLightSensorPathFollower(
            VirtualLightSensor(
                -Transformation.fromPose(vector2DOf(0.27, 0.0), 0.toRad()),
                Circle(radius = 0.3, vertexCount = 64)
            )
        )

    var mode = Idle
    var enabled = false
    var tempIndex = 0
    val parser = buildParser {
        this["record"] = {
            when (mode) {
                Record      -> "Recording"
                Mode.Follow -> "Is following now"
                Idle        -> {
                    mode = Record
                    launch {
                        val temporary = SimpleLogger("临时路径存储${tempIndex++}")
                        for ((_, current) in robotOnMap) {
                            if (mode != Record) break
                            path.record(current)
                            temporary.logWithoutStamp("${current.p.x}, ${current.p.y}, ${current.d.asRadian()}")
                            temporary.flush()
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
            remote?.paintFrame3("path", emptyList())
            "path cleared"
        }
        this["show"] = {
            with(path.get()) {
                remote?.paintPoses("路径", this)
                "path count = ${size}\n${joinToString("\n")}"
            }
        }

        this["save"] = { path.saveTo(file); "${path.size} nodes saved" }
        this["load"] = {
            mode = Idle
            path.loadFrom(file)
            remote?.paintPoses("路径", path.get())
            "${path.size} nodes loaded"
        }
        this["delete"] = { file.writeText(""); "path save deleted" }

        this["init"] = {
            when (mode) {
                Record      -> "Is Recording now."
                Mode.Follow -> "Is Following now."
                Idle        -> {
                    launch {
                        val t0 = System.currentTimeMillis()
                        while (System.currentTimeMillis() - t0 < 5)
                            if (enabled) commandOut.send(velocity(.0, +PI / 10))
                        while (System.currentTimeMillis() - t0 < 15)
                            if (enabled) commandOut.send(velocity(.0, -PI / 10))
                        while (System.currentTimeMillis() - t0 < 20)
                            if (enabled) commandOut.send(velocity(.0, +PI / 10))
                    }
                    "Ok."
                }
            }
        }
        this["go"] = f@{
            when (mode) {
                Record      -> "Is Recording now."
                Mode.Follow -> "Ok."
                Idle        -> {
                    if (path.size < 2) return@f "No path."
                    mode = Mode.Follow
                    follower.path = path.get()
                    launch {
                        while (isActive && mode == Mode.Follow) {
                            follower(robotOnMap.receive().data)
                                .let { command ->
                                    when (command) {
                                        is Follow -> {
                                            val (v, w) = command
                                            if (enabled) commandOut.send(velocity(v, w))
                                        }
                                        else      -> {
                                            println(command)
                                            when (command) {
                                                is Turn   -> {
                                                    val (angle) = command
                                                    if (enabled) commandOut.send(velocity(.0, .0))
                                                    delay(200L)
                                                    val temp = if (angle < -2 * PI / 3) angle + 2 * PI else angle
                                                    val delta = abs(temp)
                                                    val w = temp.sign * PI / 10
                                                    val (_, d0) = robotOnMap.receive().data
//                                                  while (true) {
//                                                      if (enabled) commandOut.send(velocity(.1, 0))
//                                                      val (p, _) = robotOnMap.receive().data
//                                                      if ((p - p0).norm() > .03) break
//                                                  }
                                                    while (true) {
                                                        if (enabled) commandOut.send(velocity(.0, w))
                                                        val (_, d) = robotOnMap.receive().data
                                                        if (abs(d.asRadian() - d0.asRadian()) > delta) break
                                                    }
                                                }
                                                is Error  -> Unit
                                                is Finish -> mode = Idle
                                            }
                                        }
                                    }
                                }
                            if (mode != Mode.Follow) enabled = false
                            if (!enabled) commandOut.send(velocity(.0, .0))
                            remote?.run {
                                val shape = follower.sensor.areaShape
                                shape.firstOrNull()?.let { paintVectors("传感器", shape + it) }
                                paintPoses("尖点", listOf(follower.tip))
                            }
                        }
                    }
                    "Ok."
                }
            }
        }
        this["\'"] = {
            enabled = !enabled
            if (enabled) "!" else "?"
        }

        this["state"] = { mode }
        this["shutdown"] = { cancel(); "Bye~" }
    }

    /** 从控制台阻塞解析 */
    launch {
        while (!robotOnMap.isClosedForReceive)
            withContext(coroutineContext) { readLine() }
                ?.let(parser::invoke)
                ?.map(::feedback)
                ?.forEach(::display)
    }
}
