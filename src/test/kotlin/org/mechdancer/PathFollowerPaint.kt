package org.mechdancer

import cn.autolabor.pathfollower.Circle
import cn.autolabor.pathfollower.VirtualLightSensor
import cn.autolabor.pathfollower.VirtualLightSensorPathFollower
import cn.autolabor.pathfollower.VirtualLightSensorPathFollower.FollowCommand
import cn.autolabor.pathfollower.VirtualLightSensorPathFollower.FollowCommand.*
import cn.autolabor.pathmaneger.loadTo
import cn.autolabor.pathmaneger.saveTo
import cn.autolabor.pm1.Resource
import cn.autolabor.pm1.sdk.PM1
import cn.autolabor.transform.Transformation
import org.mechdancer.Mode.*
import org.mechdancer.Mode.Follow
import org.mechdancer.algebra.function.vector.minus
import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.console.parser.buildParser
import org.mechdancer.console.parser.display
import org.mechdancer.console.parser.feedback
import org.mechdancer.dependency.must
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.remote.resources.MulticastSockets
import java.io.File
import java.net.InetSocketAddress
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.sign

enum class Mode {
    Record,
    Follow,
    Idle
}

fun main() {
    var mode = Idle
    var enabled = false
    val followLock = Object()

    var running = true

    var fromMap = Transformation.unit(2)
    val path = mutableListOf<Vector2D>()

    val file = File("path.txt")
    val remote = remoteHub(
        name = "path follower test",
        address = InetSocketAddress("238.88.8.100", 30000))
    val pm1 = Resource { odometry ->
        val p = vector2DOf(odometry.x, odometry.y)
        fromMap = -Transformation.fromPose(p, odometry.theta.toRad())
        if (mode == Record && path.lastOrNull()?.let { (it - p).norm() > 0.05 } != false) {
            path += p
            remote.paintFrame2("path", path.map { it.x to it.y })
        }
        remote.paint("odometry", odometry.x, odometry.y, odometry.theta)
    }
    val follower =
        VirtualLightSensor(
            -Transformation.fromPose(vector2DOf(0.15, 0.0), 0.toRad()),
            Circle(radius = 0.2, vertexCount = 64)
        ).let { VirtualLightSensorPathFollower(it) }

    val parser = buildParser {
        this["record"] = {
            when (mode) {
                Record -> "Recording"
                Follow -> "Is following now"
                Idle   -> {
                    mode = Record
                    "Recording"
                }
            }
        }
        this["pause"] = {
            when (mode) {
                Record -> {
                    mode = Idle
                    "Paused"
                }
                Follow -> "Is following now"
                Idle   -> "..."
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
                Record -> "Is Recording now."
                Follow -> "Ok."
                Idle   -> {
                    mode = Follow
                    follower.path = path
                    synchronized(followLock) { followLock.notify() }
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

    // launch pm1
    PM1.locked = false
    PM1.setCommandEnabled(false)
    launchBlocking { pm1() }

    // launch parser
    thread {
        while (running) readLine()
            ?.let(parser::invoke)
            ?.map(::feedback)
            ?.forEach(::display)
        pm1.close()
    }

    // launch sensor
    thread {
        synchronized(followLock) {
            while (true) {
                if (mode == Follow) {
                    when (val command = follower(fromMap)) {
                        is FollowCommand.Follow -> {
                            val (v, w) = command
                            PM1.drive(v, w)
                        }
                        is Turn                 -> {
                            val (angle) = command
                            println("turn: $angle")
                            PM1.drive(.0, .0)
                            Thread.sleep(200)
                            PM1.driveSpatial(.050, .0, .025, .0)
                            PM1.driveSpatial(.0, angle.sign * .5, .0, abs(angle))
                        }
                        Error                   -> {
                            println("error")
                            mode = Idle
                        }
                        Finish                  -> {
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
                } else {
                    followLock.wait()
                }
            }
        }
    }

    // launch network
    remote.openAllNetworks()
    println("remote launched on ${remote.components.must<MulticastSockets>().address}")
    while (true) remote()
}
