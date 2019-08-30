package org.mechdancer

import cn.autolabor.locator.ParticleFilter
import cn.autolabor.pathfollower.Circle
import cn.autolabor.pathfollower.VirtualLightSensor
import cn.autolabor.pathfollower.VirtualLightSensorPathFollower
import cn.autolabor.pathfollower.VirtualLightSensorPathFollower.FollowCommand
import cn.autolabor.pathfollower.VirtualLightSensorPathFollower.FollowCommand.*
import cn.autolabor.pathmaneger.loadTo
import cn.autolabor.pathmaneger.saveTo
import cn.autolabor.pm1.sdk.PM1
import cn.autolabor.transform.Transformation
import cn.autolabor.utilities.Odometry
import cn.autolabor.utilities.time.Stamped
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

fun main() {
    val remote = remoteHub(name = "baafs test", address = InetSocketAddress("238.88.8.100", 30000))

    // 滤波器
    val filter = ParticleFilter(128)
    var mapToBaseLink = Transformation.unit(2)
    // 业务状态
    var mode = Idle
    var enabled = false
    val followLock = Object()
    val path = mutableListOf<Vector2D>()
    // 控制台解析器运行
    var running = true
    // 需要阻塞执行的资源
    val marvelmind = com.marvelmind.Resource { time, x, y ->
        remote.paint("marvelmind", x, y)
        filter.measureHelper(Stamped(time, vector2DOf(x, y)))
    }
    val pm1 = cn.autolabor.pm1.Resource { (stamp, _, _, x, y, theta) ->
        val inner = Stamped(stamp, Odometry(vector2DOf(x, y), theta.toRad()))

        remote.paint("odometry", x, y, theta)
        filter.measureMaster(inner)

        // 调试
        remote.paintFrame3("particles", filter.particles.map { (odom, _) -> Triple(odom.p.x, odom.p.y, odom.d.value) })
        remote.paintFrame2("life", filter.particles.mapIndexed { i, (_, n) -> i.toDouble() to n.toDouble() })

        val (measureWeight, particleWeight) = filter.weightTemp
        remote.paint("定位权重", measureWeight)
        remote.paint("粒子权重", particleWeight)

        filter[inner]
            ?.also { (p, d) ->
                remote.paint("filter", p.x, p.y, d.value)
                mapToBaseLink = -Transformation.fromPose(p, d)
                if (mode == Record && path.lastOrNull()?.let { (it - p).norm() > 0.05 } != false) {
                    path += p
                    remote.paintFrame2("path", path.map { it.x to it.y })
                }
            }
    }
    val follower = run {
        VirtualLightSensorPathFollower(
            VirtualLightSensor(
                fromBaseLink = -Transformation.fromPose(vector2DOf(0.15, 0.0), 0.toRad()),
                lightRange = Circle(radius = 0.2, vertexCount = 64)
            ))
    }
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
                    follower(mapToBaseLink)
                        .let {
                            when (it) {
                                is FollowCommand.Follow -> {
                                    val (v, w) = it
                                    PM1.drive(v, w)
                                }
                                is Turn                 -> {
                                    val (angle) = it
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
