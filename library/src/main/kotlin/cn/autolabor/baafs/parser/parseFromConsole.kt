package cn.autolabor.baafs.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mechdancer.console.parser.Parser
import org.mechdancer.console.parser.display
import org.mechdancer.console.parser.feedback

/** 从控制台解析一行并在控制台上打印反馈 */
suspend fun Parser.parseFromConsole() {
    print(">> ")
    withContext(Dispatchers.IO) { readLine() }
        ?.let(this::invoke)
        ?.map(::feedback)
        ?.forEach(::display)
}
