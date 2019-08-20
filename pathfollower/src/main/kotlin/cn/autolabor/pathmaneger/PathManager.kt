package cn.autolabor.pathmaneger

import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import java.io.File

infix fun File.save(list: Iterable<Vector2D>) =
    writeText(list.joinToString("\n") { "${it.x}, ${it.y}" })

fun File.load() =
    readLines()
        .map {
            val comma = it.indexOf(',')
            vector2DOf(it.substring(0 until comma).toDouble(),
                       it.substring(comma + 1).toDouble())
        }

infix fun File.loadTo(list: MutableCollection<Vector2D>) {
    list.clear()
    list.addAll(load())
}
