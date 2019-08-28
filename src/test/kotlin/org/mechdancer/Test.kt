package org.mechdancer

import cn.autolabor.pathfollower.Circle
import cn.autolabor.pathfollower.VirtualLightSensor
import cn.autolabor.pathfollower.VirtualLightSensorPathFollower
import cn.autolabor.pathmaneger.loadTo
import cn.autolabor.pathmaneger.save
import cn.autolabor.pm1.Resource
import cn.autolabor.pm1.sdk.PM1
import cn.autolabor.transform.Transformation
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

fun main() {
    var record = false
    var running = true
    val followLock = Object()
    var follow = false
    var enabled = false
    var fromMap = Transformation.unit(2)

    val path = mutableListOf<Vector2D>()

    val file = File("path.txt")
    val painter = remoteHub(
        name = "path follower test",
        address = InetSocketAddress("238.88.8.100", 30000)
    )
    val pm1 = Resource { odometry ->
        val p = vector2DOf(odometry.x, odometry.y)
        fromMap = -Transformation.fromPose(p, odometry.theta.toRad())
        if (record && path.lastOrNull()?.let { (it - p).norm() > 0.05 } != false) {
            path += p
            painter.paintFrame2("path", path.map { it.x to it.y })
        }
        painter.paint("odometry", odometry.x, odometry.y, odometry.theta)
    }
    val follower =
        VirtualLightSensor(
            -Transformation.fromPose(vector2DOf(0.15, 0.0), 0.toRad()),
            Circle(radius = 0.2, vertexCount = 64)
        ).let { VirtualLightSensorPathFollower(it) }

    val parser = buildParser {
        this["record"] = { record = true; "recording" }
        this["pause"] = { record = false; "paused" }
        this["clear"] = {
            record = false
            path.clear()
            painter.paintFrame2("path", emptyList())
            "path cleared"
        }
        this["show"] = {
            buildString {
                appendln("path count = ${path.size}")
                for (node in path) appendln("${node.x}\t${node.y}")
            }
        }

        this["save"] = { file save path; "${path.size} nodes saved" }
        this["load"] = {
            record = false
            file loadTo path
            painter.paintFrame2("path", path.map { it.x to it.y })
            "${path.size} nodes loaded"
        }
        this["delete"] = { file.writeText(""); "path save deleted" }

        this["go"] = {
            when {
                record         -> "Are you sure? I'm recording now!"
                path.isEmpty() -> "No path, please load a path."
                else           -> {
                    follow = true
                    follower.path = path
                    synchronized(followLock) { followLock.notify() }
                    "Ok."
                }
            }
        }
        this["stop"] = { follow = false; "Ok." }
        this["\'"] = {
            enabled = !enabled
            PM1.setCommandEnabled(enabled)
            if (enabled) "!" else "?"
        }

        this["state"] = {
            when {
                record -> "recording"
                follow -> "following"
                else   -> "idle"
            }
        }
        this["shutdown"] = { running = false; "Bye~" }
    }

    // launch pm1
    PM1.locked = false
    PM1.setCommandEnabled(false)
    launchBlocking { pm1() }

    // launch network
    painter.openAllNetworks()
    launchBlocking(10000) { painter.yell() }
    launchBlocking { painter() }
    println("remote launched on ${painter.components.must<MulticastSockets>().address}")

    // launch parser
    thread {
        while (running) readLine()
            ?.let(parser::invoke)
            ?.map(::feedback)
            ?.forEach(::display)
        pm1.close()
    }

    // launch sensor
    synchronized(followLock) {
        while (true) {
            if (follow) {
                follower(fromMap)
                    .let { (v, w) ->
                        when (v) {
                            null -> when (w) {
                                null -> {
                                    follow = false
                                    PM1.setCommandEnabled(false)
                                    println("error")
                                }
                                else -> {
                                    PM1.drive(.0, .0)
                                    println("turn: $w")
                                    Thread.sleep(200)
                                    PM1.driveSpatial(.05, .0, .03, .0)
                                    PM1.driveSpatial(.0, w.sign * .5, .0, abs(w))
                                }
                            }
                            else -> when (w) {
                                null -> {
                                    follow = false
                                    PM1.setCommandEnabled(false)
                                    println("finish")
                                }
                                else ->
                                    PM1.drive(v, w)
                            }
                        }
                    }
                follower
                    .sensor
                    .rangeShape
                    .map { it.x to it.y }
                    .let { it + it.first() }
                    .let { painter.paintFrame2("sensor", it) }
                Thread.sleep(100)
            } else {
                followLock.wait()
            }
        }
    }
}
