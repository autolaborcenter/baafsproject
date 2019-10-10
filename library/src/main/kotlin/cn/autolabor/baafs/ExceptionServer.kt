package cn.autolabor.baafs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.mechdancer.SimpleLogger
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.console.parser.Parser
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.exceptions.ExceptionMessage.Occurred
import org.mechdancer.exceptions.ExceptionMessage.Recovered
import org.mechdancer.exceptions.RecoverableException

fun CoroutineScope.startExceptionServer(
    exceptions: ReceiveChannel<ExceptionMessage<*>>,
    commandIn: ReceiveChannel<NonOmnidirectional>,
    commandOut: SendChannel<NonOmnidirectional>,
    parser: Parser
) {
    val set = mutableSetOf<RecoverableException>()
    val logger = SimpleLogger("exceptions")
    parser["exceptions"] = { synchronized(set) { set.joinToString("\n") } }
    launch {
        for (exception in exceptions) {
            val what = exception.what
            synchronized(set) {
                when (exception) {
                    is Occurred<*>  -> {
                        if (set.add(what)) {
                            System.err.println(what)
                            logger.log(what)
                        }
                    }
                    is Recovered<*> -> {
                        if (set.remove(exception.what))
                            logger.log("${what::class.simpleName}: recovered")
                    }
                }
            }
        }
    }
    launch {
        for (command in commandIn)
            if (set.isEmpty())
                commandOut.send(command)
    }.invokeOnCompletion {
        commandOut.close()
    }
}
