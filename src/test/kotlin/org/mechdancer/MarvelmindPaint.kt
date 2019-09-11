package org.mechdancer

import com.marvelmind.Resource
import org.mechdancer.modules.Default

fun main() {
    val remote = Default.remote
    var i = 0
    // launch marvelmind
    Resource { _, x, y ->
        println("${++i}: $x $y")
        remote.paint("marvelmind", x, y)
    }.use { while (true) it() }
}
