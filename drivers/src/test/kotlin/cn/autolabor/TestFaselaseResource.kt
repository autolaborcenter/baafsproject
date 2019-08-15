package cn.autolabor

import com.faselase.Resource
import kotlin.math.cos
import kotlin.math.sin

fun main() {
    val lidar = Resource { begin, end, list ->
        println("time: ${end - begin}, number: ${list.size}, rad: ${list.last().second - list.first().second}")
        list.map { (rho, theta) -> "${rho * cos(theta)} ${rho * sin(theta)}" }.forEach(::println)
    }
    while (true) lidar()
}
