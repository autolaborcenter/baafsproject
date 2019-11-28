package cn.autolabor.baafs.parser

import cn.autolabor.business.Business
import cn.autolabor.business.Business.Functions.Following
import cn.autolabor.business.Business.Functions.Recording
import kotlinx.coroutines.*
import org.mechdancer.console.parser.Parser
import org.mechdancer.console.parser.numbers

@ExperimentalCoroutinesApi
fun CoroutineScope.registerBusinessParser(
    business: Business,
    parser: Parser
) {
    parser["cancel"] = {
        runBlocking(coroutineContext) { business.cancel() }
        "current mode: ${business.function?.toString() ?: "Idle"}"
    }
    parser["record"] = {
        runBlocking(coroutineContext) { business.startRecording() }
        "current mode: ${business.function?.toString() ?: "Idle"}"
    }
    parser["clear"] = {
        (business.function as? Recording)
            ?.clear()
            ?.let { "path cleared" }
        ?: "cannot clear recorded path unless when recording"
    }

    parser["save @name"] = {
        val name = get(1).toString()
        (business.function as? Recording)
            ?.save(name)
            ?.let { "$it nodes were saved in $name" }
        ?: "cannot save recorded path unless when recording"
    }
    parser["refresh @name"] = {
        runBlocking(coroutineContext) { business.cancel() }
        val name = get(1).toString()
        business.globals.refresh(name, .0)
            ?.let {
                launch { business.startFollowing(it) }
                "${it.size} nodes loaded from $name"
            }
        ?: "no path named $name"
    }
    parser["load @name"] = {
        runBlocking(coroutineContext) { business.cancel() }
        val name = get(1).toString()
        business.globals.load(name, .0)
            ?.let {
                launch { business.startFollowing(it) }
                "${it.size} nodes loaded from $name"
            }
        ?: "no path named $name"
    }
    parser["load @name @num%"] = {
        runBlocking(coroutineContext) { business.cancel() }
        val name = get(1).toString()
        business.globals.load(name, numbers.single() / 100)
            ?.let {
                launch { business.startFollowing(it) }
                "${it.size} nodes loaded from $name"
            }
        ?: "no path named $name"
    }
    parser["resume @name"] = {
        runBlocking(coroutineContext) { business.cancel() }
        val name = get(1).toString()
        business.globals.resume(name)
            ?.let {
                launch { business.startFollowing(it) }
                "${it.size} nodes loaded from $name"
            }
        ?: "no path named $name"
    }

    val formatter = java.text.DecimalFormat("0.00")
    parser["progress"] = {
        (business.function as? Following)
            ?.let { "progress = ${formatter.format(it.global.progress * 100)}%" }
        ?: business.globals.toString()
    }

    parser["function"] = { business.function?.toString() ?: "Idle" }
    parser["shutdown"] = { cancel(); "Bye~" }
}
