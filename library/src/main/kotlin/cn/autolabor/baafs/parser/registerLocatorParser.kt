package cn.autolabor.baafs.parser

import cn.autolabor.locator.ParticleFilter
import org.mechdancer.console.parser.Parser

fun registerParticleFilterParser(
    particleFilter: ParticleFilter,
    parser: Parser
) {
    parser["fusion state"] = {
        buildString {
            val now = System.currentTimeMillis()
            appendLine(particleFilter.lastQuery
                           ?.let { (t, pose) -> "last locate at $pose ${now - t}ms ago" }
                       ?: "never query pose before")
            val (t, quality) = particleFilter.quality
            appendLine("particles last update ${now - t}ms ago")
            appendLine("now system is ${if (particleFilter.isConvergent) "" else "not "}ready for work")
            append("quality = $quality")
        }
    }
}
