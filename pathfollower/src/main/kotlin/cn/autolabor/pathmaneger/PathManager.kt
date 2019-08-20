package cn.autolabor.pathmaneger

import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import java.io.File

fun File.save(point: Vector2D) =
    writeText("${point.x}, ${point.y}")

fun File.read() =
    readLines()
        .map {
            val comma = it.indexOf(',')
            vector2DOf(it.substring(0 until comma).toDouble(),
                       it.substring(comma + 1).toDouble())
        }
