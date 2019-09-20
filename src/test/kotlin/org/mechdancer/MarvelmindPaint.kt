package org.mechdancer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.mechdancer.modules.await
import org.mechdancer.modules.devices.Locator.FrameworkRemoteLocator.MarvelmindLocator

fun main() {
    val scope = CoroutineScope(Dispatchers.Default)
    var i = 0
    scope.launch {
        for ((_, v) in MarvelmindLocator(this).robotLocation)
            println("${++i}: ${v.x} ${v.y}")
    }
    scope.await()
}
