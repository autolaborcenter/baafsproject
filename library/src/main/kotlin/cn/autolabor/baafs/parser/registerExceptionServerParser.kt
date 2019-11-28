package cn.autolabor.baafs.parser

import org.mechdancer.console.parser.Parser
import org.mechdancer.exceptions.ExceptionServer

fun registerExceptionServerParser(
    exceptionServer: ExceptionServer,
    parser: Parser
) {
    parser["exceptions"] = { exceptionServer.get().joinToString("\n") }
}
