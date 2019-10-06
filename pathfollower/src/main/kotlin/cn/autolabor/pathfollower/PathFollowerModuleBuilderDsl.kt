package cn.autolabor.pathfollower

import cn.autolabor.pathfollower.Mode.*
import cn.autolabor.pathfollower.algorithm.PathFollowerBuilderDsl
import cn.autolabor.pathfollower.algorithm.PathFollowerBuilderDsl.Companion.pathFollower
import kotlinx.coroutines.*
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.mechdancer.BuilderDslMarker
import org.mechdancer.SimpleLogger
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.console.parser.buildParser
import org.mechdancer.console.parser.display
import org.mechdancer.console.parser.feedback
import org.mechdancer.paintFrame3
import org.mechdancer.paintPoses
import org.mechdancer.remote.presets.RemoteHub
import java.io.File
import kotlin.math.PI

@BuilderDslMarker
class PathFollowerModuleBuilderDsl private constructor() {
    // 路径记录间隔
    var pathInterval: Double = .05
    // 原地转方向分界
    var directionLimit = -2 * PI / 3
    // 日志配置
    var logger: SimpleLogger? = SimpleLogger("PathFollowerModule")
    // 绘图配置
    var painter: RemoteHub? = null
    // 循径控制器配置
    private var followerConfig: PathFollowerBuilderDsl.() -> Unit = {}

    fun follower(block: PathFollowerBuilderDsl.() -> Unit) {
        followerConfig = block
    }

    companion object {
        /**
         * 构造循径模块
         */
        fun CoroutineScope.pathFollowerModule(
            robotOnMap: ReceiveChannel<Stamped<Odometry>>,
            commandOut: SendChannel<NonOmnidirectional>,
            block: PathFollowerModuleBuilderDsl.() -> Unit = {}
        ) =
            PathFollowerModuleBuilderDsl()
                .apply(block)
                .apply {
                    require(pathInterval > 0)
                }.run {
                    PathFollowerModule(
                        scope = this@pathFollowerModule,
                        robotOnMap = robotOnMap,
                        commandOut = commandOut,
                        follower = pathFollower(followerConfig),
                        pathInterval = pathInterval,
                        directionLimit = directionLimit,
                        logger = logger,
                        painter = painter)
                }

        /**
         * 启动循径模块
         * 包含任务控制、路径管理、控制台解析、循径任务；
         */
        @ExperimentalCoroutinesApi
        fun CoroutineScope.startPathFollower(
            robotOnMap: ReceiveChannel<Stamped<Odometry>>,
            commandOut: SendChannel<NonOmnidirectional>,
            block: PathFollowerModuleBuilderDsl.() -> Unit = {}
        ): Job {
            val file = File("path.txt")
            val module = pathFollowerModule(robotOnMap, commandOut, block)
            val parser = buildParser {
                this["cancel"] = {
                    module.mode = Idle
                    "current mode: ${module.mode}"
                }
                this["record"] = {
                    module.mode = Record
                    "current mode: ${module.mode}"
                }
                this["clear"] = {
                    module.path.clear()
                    module.painter?.paintFrame3("路径", emptyList())
                    "path cleared"
                }
                this["show"] = {
                    with(module.path.get()) {
                        module.painter?.paintPoses("路径", this)
                        "path count = ${size}\n${joinToString("\n")}"
                    }
                }

                this["save"] = {
                    with(module.path) {
                        saveTo(file)
                        module.painter?.paintPoses("路径", get())
                        "$size nodes saved"
                    }
                }
                this["load"] = {
                    module.mode = Idle
                    with(module.path) {
                        loadFrom(file)
                        module.painter?.paintPoses("路径", get())
                        "$size nodes loaded"
                    }
                }
                this["delete"] = { file.writeText(""); "path file deleted" }

                this["go"] = {
                    module.mode = Follow(loop = false)
                    "current mode: ${module.mode}"
                }
                this["loop"] = {
                    module.mode = Follow(loop = true)
                    "current mode: ${module.mode}"
                }
                this["\'"] = {
                    module.isEnabled = !module.isEnabled
                    if (module.isEnabled) "!" else "?"
                }

                this["state"] = { module.mode }
                this["shutdown"] = { cancel(); "Bye~" }
            }

            /** 从控制台阻塞解析 */
            return launch(start = LAZY) {
                while (isActive) {
                    print(">> ")
                    (GlobalScope
                         .async { readLine() }
                         .takeIf { isActive }
                     ?: break)
                        .await()
                        ?.also { module.logger?.log("user input: $it") }
                        ?.let(parser::invoke)
                        ?.map(::feedback)
                        ?.forEach(::display)
                }
            }
        }
    }
}
