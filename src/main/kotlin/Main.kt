import AutolaborScript.ScriptConfiguration
import java.io.File
import kotlin.script.experimental.api.ScriptDiagnostic.Severity
import kotlin.script.experimental.api.onFailure
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
    println("compiling...")
    BasicJvmScriptingHost()
        .eval(file.toScriptSource(),
              ScriptConfiguration,
              createJvmEvaluationConfigurationFromTemplate<AutolaborScript>())
        .onFailure { result ->
            result.reports
                .filter { (_, level) -> level == Severity.ERROR }
                .forEach { System.err.println(it) }
        }
}
