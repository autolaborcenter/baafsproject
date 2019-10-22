package cn.autolabor.business

import cn.autolabor.business.Business.*
import cn.autolabor.pathfollower.algorithm.PathFollowerBuilderDsl
import cn.autolabor.pathfollower.algorithm.PathFollowerBuilderDsl.Companion.pathFollower
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.mechdancer.BuilderDslMarker
import org.mechdancer.SimpleLogger
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.console.parser.Parser
import org.mechdancer.console.parser.numbers
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.geometry.angle.Angle
import org.mechdancer.geometry.angle.toDegree
import org.mechdancer.paintFrame3
import org.mechdancer.paintPoses
import org.mechdancer.remote.presets.RemoteHub
import java.io.File
import java.text.DecimalFormat

@BuilderDslMarker
class PathFollowerModuleBuilderDsl private constructor() {
    // 路径记录间隔
    var pathInterval: Double = .05
    // 原地转方向分界
    var directionLimit: Angle = (-120).toDegree()
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
            robotOnOdometry: ReceiveChannel<Stamped<Odometry>>,
            commandOut: SendChannel<NonOmnidirectional>,
            exceptions: SendChannel<ExceptionMessage<FollowFailedException>>,
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
                        robotOnOdometry = robotOnOdometry,
                        commandOut = commandOut,
                        exceptions = exceptions,
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
            robotOnOdometry: ReceiveChannel<Stamped<Odometry>>,
            commandOut: SendChannel<NonOmnidirectional>,
            exceptions: SendChannel<ExceptionMessage<FollowFailedException>>,
            consoleParser: Parser,
            block: PathFollowerModuleBuilderDsl.() -> Unit = {}
        ) {
            val module = pathFollowerModule(
                robotOnMap = robotOnMap,
                robotOnOdometry = robotOnOdometry,
                commandOut = commandOut,
                exceptions = exceptions,
                block = block)
            with(consoleParser) {
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

                this["save @name"] = {
                    val name = get(1).toString()
                    with(module.path) {
                        saveTo(File(name))
                        module.painter?.paintPoses("路径", get())
                        "$size nodes saved to $name"
                    }
                }
                this["load @name"] = {
                    val name = get(1).toString()
                    val file = File(name)
                    if (!file.exists())
                        "path not exist"
                    else {
                        module.mode = Idle
                        with(module.path) {
                            loadFrom(file)
                            module.painter?.paintPoses("路径", get())
                            "$size nodes loaded"
                        }
                    }
                }

                val formatter = DecimalFormat("0.00")
                val formatProgress = {
                    "progress = ${formatter.format(module.progress * 100)}%"
                }
                this["progress"] = {
                    module.runCatching {
                        formatProgress()
                    }.getOrElse { it.message }
                }
                this["progress = @num"] = {
                    module.runCatching {
                        progress = numbers.single()
                        formatProgress()
                    }.getOrElse { it.message }
                }
                this["progress = @num%"] = {
                    module.runCatching {
                        progress = numbers.single() / 100
                        formatProgress()
                    }.getOrElse { it.message }
                }

                this["go"] = {
                    module.mode = Follow(loop = false)
                    "current mode: ${module.mode}"
                }
                this["loop"] = {
                    module.mode = Follow(loop = true)
                    "current mode: ${module.mode}"
                }
                this["\'"] = {
                    module.runCatching {
                        isEnabled = !isEnabled
                        if (isEnabled) "continue" else "paused"
                    }.getOrElse { it.message }
                }

                this["state"] = { module.mode }
                this["shutdown"] = { cancel(); "Bye~" }
            }
        }
    }
}
