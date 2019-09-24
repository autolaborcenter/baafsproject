package org.mechdancer

import com.faselase.Resource
import org.mechdancer.modules.Default.remote

fun main() {
    var time = System.currentTimeMillis()
    val faselase = Resource { list ->
        val now = System.currentTimeMillis()
        if (now - time > 20) {
            time = now
            remote.paintVectors("faselase", list.map { it.data.toVector2D() })
        }
    }
    while (true) faselase()
}
