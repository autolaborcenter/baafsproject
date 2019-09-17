package org.mechdancer

import kotlinx.coroutines.runBlocking
import org.mechdancer.modules.devices.Locator.MarvelmindLocator

fun main() = runBlocking {
    var i = 0
    for ((_, v) in MarvelmindLocator(this).robotLocation)
        println("${++i}: ${v.x} ${v.y}")
}
