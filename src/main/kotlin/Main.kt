import AutolaborScript.ScriptConfiguration
import kotlinx.coroutines.*
import java.io.File
import kotlin.script.experimental.api.ScriptDiagnostic.Severity
import kotlin.script.experimental.api.onFailure
import kotlin.script.experimental.api.onSuccess
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmEvaluationConfigurationFromTemplate

fun main(args: Array<String>) {
    // 查找脚本文件
    val fileName = args.lastOrNull()?.takeUnless { it.startsWith("-") } ?: "default.autolabor.kts"
    val file = File(fileName)
    if (!file.exists()) {
        System.err.println("Cannot find script. Please Check your path: ${file.absolutePath}.")
        return
    }
    runBlocking(Dispatchers.Default) {
        val job = launch {
            print("compiling")
            while (true) {
                delay(800L)
                print('.')
            }
        }
        with(BasicJvmScriptingHost()) {
            compiler(
                file.toScriptSource(),
                ScriptConfiguration
            ).onSuccess {
                job.cancelAndJoin()
                println("done")
                evaluator(it, createJvmEvaluationConfigurationFromTemplate<AutolaborScript>())
            }.onFailure { result ->
                job.cancelAndJoin()
                println()
                result.reports
                    .filter { (_, level) -> level == Severity.ERROR }
                    .forEach { System.err.println(it) }
            }
        }
    }
}
