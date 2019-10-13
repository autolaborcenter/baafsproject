package cn.autolabor

import com.faselase.loadAsScan
import com.faselase.saveToBmp
import org.mechdancer.algebra.implement.vector.vector2DOf
import java.io.File

fun main() {
    listOf(vector2DOf(0, 0),
           vector2DOf(1, 1),
           vector2DOf(2, 2)
    ).saveToBmp("test")
    File("test.bmp")
        .loadAsScan()
        .joinToString("\n") { "${it.x} ${it.y}" }
        .let(::println)
}