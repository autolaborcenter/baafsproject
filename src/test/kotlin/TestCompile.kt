import AutolaborScript.ScriptConfiguration
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import kotlin.script.experimental.api.ScriptDiagnostic.Severity.ERROR
import kotlin.script.experimental.api.onFailure
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.test.fail

class TestCompile {
    @Test
    fun test() {
        // 查找脚本文件
        val file = File("default.autolabor.kts")
        assert(file.exists())
        runBlocking {
            BasicJvmScriptingHost().compiler(file.toScriptSource(), ScriptConfiguration)
        }.onFailure { result ->
            fail("failed to compile with message: \n${result.reports.filter { it.severity == ERROR }.joinToString("\n")}")
        }
    }
}
