package cn.autolabor.baafs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.mechdancer.channel
import org.mechdancer.common.Velocity.Companion.velocity
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.console.parser.buildParser
import org.mechdancer.console.parser.feedback
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.exceptions.ExceptionMessage.Occurred
import org.mechdancer.exceptions.ExceptionMessage.Recovered
import org.mechdancer.exceptions.RecoverableException
import org.mechdancer.remote.modules.tcpconnection.connectionListener
import org.mechdancer.remote.modules.tcpconnection.listenString
import org.mechdancer.remote.modules.tcpconnection.say
import org.mechdancer.remote.presets.remoteHub
import org.mechdancer.remote.resources.TcpCmd
import kotlin.concurrent.thread

object TestExceptionServer {
    @ExperimentalStdlibApi
    @JvmStatic
    fun main(args: Array<String>) = runBlocking<Unit>(Dispatchers.Default) {
        val exceptions = channel<ExceptionMessage<*>>()
        val commandToFilter = channel<NonOmnidirectional>()
        val commandToRobot = channel<NonOmnidirectional>()
        val parser = buildParser {
            this["throw"] = {
                this@runBlocking.launch { exceptions.send(Occurred(RecoverableException("e"))) }
                "."
            }
            this["recover"] = {
                this@runBlocking.launch { exceptions.send(Recovered(RecoverableException("e"))) }
                "."
            }
        }

        startExceptionServer(
            exceptions = exceptions,
            commandIn = commandToFilter,
            commandOut = commandToRobot,
            parser = parser)

        launch {
            while (true) {
                commandToFilter.send(velocity(.1, 0))
                delay(1000)
            }
        }
        launch {
            for (command in commandToRobot)
                println(command)
        }

        remoteHub("command server") {
            inAddition {
                connectionListener { client, I ->
                    client
                        .endsWith("client") // 只接受名称符合规则的连接
                        .also {
                            if (it) {
                                while (true) {
                                    val msg = I.listenString()
                                    println("- hear $msg")
                                    parser(msg)
                                        .map(::feedback)
                                        .single()
                                        .second
                                        .run { I.say(toString()) }
                                }
                            }
                        }
                }
            }
        }.apply {
            openAllNetworks()
            thread(isDaemon = true) { while (true) invoke() }
            thread(isDaemon = true) { while (true) accept() }
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
                while (null == connect("command server", TcpCmd.COMMON) {
                        while (true) {
                            val msg = readLine()!!
                            it.say(msg)
                            if (msg == "over") break
                            println(it.listenString())
                        }
                    }) Thread.sleep(100)
            }
        }
    }
}
