package cn.autolabor

import com.faselase.Resource

fun main() {
    val lidar = Resource { list ->
        println("time: ${list.last().time - list.first().time}, number: ${list.size}, rad: ${list.last().data.angle - list.first().data.angle}")
        list.asSequence()
            .map { it.data.toVector2D() }
            .map { "${it.x} ${it.y}" }
            .joinToString("\n")
            .let(::println)
    }
    while (true) lidar()
}
