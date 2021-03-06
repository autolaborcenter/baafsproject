import AutolaborScript.ScriptConfiguration
import org.jetbrains.kotlin.mainKts.Import
import org.jetbrains.kotlin.mainKts.MainKtsConfigurator
import org.jetbrains.kotlin.mainKts.MainKtsEvaluationConfiguration
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.dependencies.DependsOn
import kotlin.script.experimental.dependencies.Repository
import kotlin.script.experimental.jvm.dependenciesFromClassContext
import kotlin.script.experimental.jvm.jvm

@KotlinScript(
        fileExtension = "autolabor.kts",
        compilationConfiguration = ScriptConfiguration::class,
        evaluationConfiguration = MainKtsEvaluationConfiguration::class)
internal abstract class AutolaborScript(val args: Array<String>) {
    internal object ScriptConfiguration : ScriptCompilationConfiguration(
            {
                defaultImports(DependsOn::class, Repository::class, Import::class)
                defaultImports("kotlin.math.*")
                defaultImports("kotlinx.coroutines.*")
                jvm {
                    dependenciesFromClassContext(
                            ScriptConfiguration::class,
                            wholeClasspath = true)
                }
                refineConfiguration {
                    // if these annotations are found on script parsing call this handler to refine configuration parameters
                    onAnnotations(
                            DependsOn::class, Repository::class, Import::class,
                            handler = MainKtsConfigurator())
                }
            })
}
