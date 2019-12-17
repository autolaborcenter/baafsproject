package org.mechdancer

import org.mechdancer.console.parser.buildParser
import org.mechdancer.console.parser.display
import org.mechdancer.console.parser.feedback
import org.mechdancer.remote.presets.remoteHub
import java.io.File

fun main() {
    val remote = remoteHub("map")
    remote.openAllNetworks()
    println(remote.networksInfo())

    val parser = buildParser {
        this["display @name"] = {
            val name = get(1).data.toString()
            File(name).useLines { lines ->
                lines.map { it.split(',').map(String::toDouble) }
                    .forEach { (x, y, theta) -> remote.paint(name, x, y, theta) }
            }
            "$name done"
        }
    }

    while (true)
        readLine()
            ?.let(parser::invoke)
            ?.map(::feedback)
            ?.forEach(::display)
}
