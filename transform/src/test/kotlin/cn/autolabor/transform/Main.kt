package cn.autolabor.transform

import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.geometry.angle.toRad
import kotlin.math.PI

fun main() {
    val system = TransformSystem<String>()

    system["odometry" to "base_link"] = Transformation.fromPose(vector2DOf(1, 2), 0.toRad())
    system["map" to "base_link"] = Transformation.fromPose(vector2DOf(-10, -2), (PI / 4).toRad())

    Thread.sleep(1000)
    println(system)
    system["odometry" to "map"]
        ?.let { (cost, path, transformation) ->
            println("find transformation from odometry to map:")
            println("cost : $cost")
            println("path : ${path.joinToString("->")}")
            println("transformation : ")
            println(transformation)
        }
    ?: println("cannot find transformation")
}
