package cn.autolabor.baafs.parser

import cn.autolabor.baafs.bussiness.Business
import cn.autolabor.baafs.bussiness.Business.Functions.Following
import cn.autolabor.baafs.bussiness.Business.Functions.Recording
import com.usarthmi.UsartHmi
import kotlinx.coroutines.*
import org.mechdancer.console.parser.Parser

@ExperimentalCoroutinesApi
fun CoroutineScope.registerBusinessParser(
    business: Business,
    hmi: UsartHmi,
    parser: Parser
) {
    parser["cancel"] = {
        runBlocking(coroutineContext) { business.cancel() }
        hmi.page = UsartHmi.Page.Index
        "current mode: ${business.function?.toString() ?: "Idle"}"
    }

    parser["record"] = {
        runBlocking(coroutineContext) { business.startRecording() }
        hmi.page = UsartHmi.Page.Record
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
            ?.let {
                launch { hmi.write("t0.txt=\"${it}点已保存\"") }
                "$it nodes were saved in $name"
            }
        ?: "cannot save recorded path unless when recording"
    }

    parser["loop on"] = {
        (business.function as? Following)
            ?.run { planner.isLoopOn = true; "loop on" }
        ?: "cannot set loop on unless when following"
    }
    parser["loop off"] = {
        (business.function as? Following)
            ?.run { planner.isLoopOn = false; "loop off" }
        ?: "cannot set loop on unless when following"
    }

    val formatter = java.text.DecimalFormat("0.00")
    parser["progress"] = {
        (business.function as? Following)
            ?.let { "progress = ${formatter.format(it.planner.progress * 100)}%" }
        ?: "it's not following now"
    }

    parser["function"] = { business.function?.toString() ?: "Idle" }
    parser["shutdown"] = {
        hmi.page = UsartHmi.Page.Waiting
        cancel()
        "Bye~"
    }
}
