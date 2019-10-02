package cn.autolabor.pathfollower

import cn.autolabor.pathfollower.Mode.*
import cn.autolabor.pathfollower.algorithm.PathFollowerBuilderDsl
import cn.autolabor.pathfollower.algorithm.PathFollowerBuilderDsl.Companion.pathFollower
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.mechdancer.BuilderDslMarker
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
class PathFollowerModuleBuilderDsl {
    private lateinit var scope: CoroutineScope
    private lateinit var robotOnMap: ReceiveChannel<Stamped<Odometry>>
    private lateinit var commandOut: SendChannel<NonOmnidirectional>

    var pathInterval: Double = .05
    var directionLimit = -2 * PI / 3

    private var followerConfig: PathFollowerBuilderDsl.() -> Unit = {}
    fun follower(block: PathFollowerBuilderDsl.() -> Unit) {
        followerConfig = block
    }

    var painter: RemoteHub? = null

    companion object {
        fun CoroutineScope.pathFollowerModule(
            robotOnMap: ReceiveChannel<Stamped<Odometry>>,
            commandOut: SendChannel<NonOmnidirectional>,
            block: PathFollowerModuleBuilderDsl.() -> Unit = {}
        ) =
            PathFollowerModuleBuilderDsl()
                .apply(block)
                .apply {
                    require(pathInterval > 0)
                }
                .also {
                    it.scope = this
                    it.robotOnMap = robotOnMap
                    it.commandOut = commandOut
                }
                .run {
                    PathFollowerModule(
                        scope = scope,
                        robotOnMap = robotOnMap,
                        commandOut = commandOut,
                        follower = pathFollower(followerConfig),
                        pathInterval = pathInterval,
                        directionLimit = directionLimit,
                        painter = painter)
                }

        /**
         * 循径模块
         *
         * 包含任务控制、路径管理、控制台解析、循径任务；
         */
        @ExperimentalCoroutinesApi
        fun CoroutineScope.startPathFollower(
            robotOnMap: ReceiveChannel<Stamped<Odometry>>,
            commandOut: SendChannel<NonOmnidirectional>,
            block: PathFollowerModuleBuilderDsl.() -> Unit = {}
        ) {
            val file = File("path.txt")
            val module = pathFollowerModule(robotOnMap, commandOut, block)
            val parser = buildParser {
                this["record"] = {
                    module.mode = Record
                    "current mode: ${module.mode}"
                }
                this["clear"] = {
                    module.path.clear()
                    module.painter?.paintFrame3("path", emptyList())
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
                this["delete"] = { file.writeText(""); "path save deleted" }

                this["go"] = {
                    module.mode = Follow
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
            launch {
                while (isActive)
                    withContext(coroutineContext) {
                        print(">> ")
                        readLine()
                    }?.let(parser::invoke)?.map(::feedback)?.forEach(::display)
            }
        }
    }
}
