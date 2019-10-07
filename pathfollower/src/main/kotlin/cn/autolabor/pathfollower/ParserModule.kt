package cn.autolabor.pathfollower

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mechdancer.console.parser.Parser
import org.mechdancer.console.parser.display
import org.mechdancer.console.parser.feedback

suspend fun Parser.parseFromConsole() {
    print(">> ")
    withContext(Dispatchers.Default) { readLine() }
        ?.let(this::invoke)
        ?.map(::feedback)
        ?.forEach(::display)
}
