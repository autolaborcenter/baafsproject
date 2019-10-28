package org.mechdancer.exceptions

import org.mechdancer.console.parser.buildParser
import org.mechdancer.console.parser.feedback
import org.mechdancer.exceptions.ExceptionMessage.Occurred
import org.mechdancer.exceptions.ExceptionMessage.Recovered
import org.mechdancer.remote.modules.tcpconnection.dialogListener
import org.mechdancer.remote.modules.tcpconnection.listen
import org.mechdancer.remote.modules.tcpconnection.say
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.remote.resources.TcpCmd
import kotlin.concurrent.thread

object TestExceptionServer {
    @ExperimentalStdlibApi
    @JvmStatic
    fun main(args: Array<String>) {
        val exceptionServer = ExceptionServer()
        val parser = buildParser {
            this["throw"] = {
                exceptionServer.update(Occurred(RecoverableException("e")))
                "."
            }
            this["recover"] = {
                exceptionServer.update(Recovered(RecoverableException("e")))
                "."
            }
            this["exceptions"] = {
                var i = 0
                exceptionServer.get().joinToString("\n") { "${++i}. $it" }
            }
        }

        remoteHub("command server") {
            inAddition {
                dialogListener { _, payload ->
                    payload
                        .decodeToString()
                        .let(parser::invoke)
                        .map(::feedback)
                        .joinToString("\n") { (_, msg) -> msg.toString() }
                        .encodeToByteArray()
                }
            }
        }.apply {
            openAllNetworks()
            thread(isDaemon = true) { while (true) invoke() }
            thread(isDaemon = true) { while (true) accept() }
        }

        var i = 0
        while (true) {
            if (exceptionServer.isEmpty()) println(i++)
            Thread.sleep(1000)
        }
    }
}

object ExceptionClient {
    @ExperimentalStdlibApi
    @JvmStatic
    fun main(args: Array<String>) {
        remoteHub("command client").apply {
            openAllNetworks()
            thread(isDaemon = true) { while (true) invoke() }
            while (true) {
                val msg = readLine()!!
                while (null == connect("command server", TcpCmd.Dialog) {
                        it.say(msg.encodeToByteArray())
                        println(it.listen().decodeToString())
                    }) Thread.sleep(100)
            }
        }
    }
}
