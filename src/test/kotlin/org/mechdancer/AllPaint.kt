package org.mechdancer

import cn.autolabor.locator.ParticleFilter
import cn.autolabor.pathfollower.Circle
import cn.autolabor.pathfollower.VirtualLightSensor
import cn.autolabor.pathfollower.VirtualLightSensorPathFollower
import cn.autolabor.pathmaneger.loadTo
import cn.autolabor.pathmaneger.saveTo
import cn.autolabor.pm1.sdk.PM1
import cn.autolabor.transform.Transformation
import cn.autolabor.utilities.Odometry
import cn.autolabor.utilities.time.Stamped
import org.mechdancer.Mode.*
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
    val remote = remoteHub(name = "baafs test", address = InetSocketAddress("238.88.8.100", 30000))

    // 滤波器
    val filter = ParticleFilter(128)
    var fromMap = Transformation.unit(2)
    // 业务状态
    var mode = Idle
    var enabled = false
    val followLock = Object()
    val path = mutableListOf<Vector2D>()
    // 控制台解析器运行
    var running = true

    val marvelmind = com.marvelmind.Resource { time, x, y ->
        remote.paint("marvelmind", x, y)
        filter.measureHelper(Stamped(time, vector2DOf(x, y)))
    }
    val pm1 = cn.autolabor.pm1.Resource { odometry ->
        val inner = Stamped(odometry.stamp,
                            Odometry(vector2DOf(odometry.x, odometry.y),
                                     odometry.theta.toRad()))
        remote.paint("odometry", odometry.x, odometry.y, odometry.theta)

        filter.measureMaster(inner)
        remote.paintFrame3("particles", filter.particles.map { (odom, _) -> Triple(odom.p.x, odom.p.y, odom.d.value) })
        remote.paintFrame2("life", filter.particles.mapIndexed { i, (_, n) -> i.toDouble() to n.toDouble() })
        val (measureWeight, particleWeight) = filter.weightTemp
        remote.paint("定位权重", measureWeight)
        remote.paint("粒子权重", particleWeight)

        filter[inner]
            ?.also { (p, d) -> remote.paint("filter", p.x, p.y, d.value) }
            ?.also { (p, _) ->
                fromMap = -Transformation.fromPose(p, odometry.theta.toRad())
                if (mode == Record && path.lastOrNull()?.let { (it - p).norm() > 0.05 } != false) {
                    path += p
                    remote.paintFrame2("path", path.map { it.x to it.y })
                }
                remote.paint("odometry", odometry.x, odometry.y, odometry.theta)
            }
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

        val file = File("path.txt")
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
    // launch marvelmind
    launchBlocking { marvelmind() }

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
                    follower(fromMap)
                        .let { (v, w) ->
                            when (v) {
                                null -> when (w) {
                                    null -> {
                                        mode = Idle
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
                                        mode = Idle
                                        println("finish")
                                    }
                                    else -> PM1.drive(v, w)
                                }
                            }
                        }
                    follower
                        .sensor
                        .areaShape
                        .map { it.x to it.y }
                        .let { it + it.first() }
                        .let { remote.paintFrame2("sensor", it) }
                    if (mode != Follow) {
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
