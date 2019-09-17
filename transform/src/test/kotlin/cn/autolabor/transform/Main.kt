package cn.autolabor.transform

import cn.autolabor.transform.TransformSystem.Companion.Constant
import cn.autolabor.transform.struct.from
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.geometry.transformation.Transformation
import kotlin.math.PI

fun main() {
    val system = TransformSystem<String>()

    system["lidar" to "base_link", Constant] =
        Transformation.fromPose(vector2DOf(0.4, 0), 0.toRad())
    system["base_link" to "odometry"] =
        Transformation.fromPose(vector2DOf(1, 2), 0.toRad())
    system["base_link" to "map"] =
        Transformation.fromPose(vector2DOf(-10, -2), (PI / 4).toRad())

    Thread.sleep(200)
    println(system)
    system["odometry" from "lidar"]
        ?.let { (cost, path, transformation) ->
            println("find transformation from odometry to map:")
            println("cost : $cost")
            println("path : ${path.joinToString("->")}")
            println("transformation : ")
            println(transformation)
        }
    ?: println("cannot find transformation")
}
