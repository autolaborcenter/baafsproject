import AutolaborScript.ScriptConfiguration
import java.io.File
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmEvaluationConfigurationFromTemplate

object Scripting {
    @JvmStatic
    fun main(args: Array<String>) {
        // 查找脚本文件
        val fileName = args.lastOrNull() ?: "default.autolabor.kts"
        val file = File("../$fileName")
        if (!file.exists()) {
            System.err.println("Cannot find script. Please Check your path: ${file.absolutePath}.")
            return
        }
        println("compiling...")
        BasicJvmScriptingHost()
            .eval(file.toScriptSource(),
                  ScriptConfiguration,
                  createJvmEvaluationConfigurationFromTemplate<AutolaborScript>())
            .run {
                if ("-debug" in args) println(reports)
            }
    }
}
