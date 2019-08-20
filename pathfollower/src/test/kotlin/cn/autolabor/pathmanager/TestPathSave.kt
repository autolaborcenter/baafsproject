package cn.autolabor.pathmanager

import cn.autolabor.pathmaneger.loadTo
import cn.autolabor.pathmaneger.save
import cn.autolabor.pm1.Resource
import cn.autolabor.pm1.sdk.PM1
import org.mechdancer.algebra.function.vector.minus
import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.console.parser.buildParser
import org.mechdancer.console.parser.display
import org.mechdancer.console.parser.feedback
import java.io.File
import kotlin.concurrent.thread

fun main() {
    var record = false
    var running = true

    val file = File("path.txt")
    val path = mutableListOf<Vector2D>()
    val pm1 = Resource { odometry ->
        val p = vector2DOf(odometry.x, odometry.y)
        if (record && path.lastOrNull()?.let { (it - p).norm() > 0.05 } != false)
            path += p
    }
    val parser = buildParser {
        this["record"] = { record = true; "recording" }
        this["pause"] = { record = false; "paused" }
        this["save"] = { file save path; "${path.size} nodes saved" }
        this["load"] = { file loadTo path; "${path.size} nodes loaded" }
        this["delete"] = { file.writeText(""); "path save deleted" }
        this["clear"] = { path.clear(); "path cleared" }
        this["state"] = { if (record) "recording" else "paused" }
        this["show"] = {
            buildString {
                appendln("path count = ${path.size}")
                for (node in path) appendln("${node.x}\t${node.y}")
            }
        }
        this["bye"] = { running = false; "bye" }
    }

    PM1.locked = false
    PM1.setCommandEnabled(false)
    thread { while (true) pm1() }
    while (running)
        readLine()
            ?.let(parser::invoke)
            ?.map(::feedback)
            ?.forEach(::display)
    pm1.close()
}
