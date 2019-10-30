package cn.autolabor.business

import cn.autolabor.business.Business.Functions.Following
import cn.autolabor.business.Business.Functions.Recording
import cn.autolabor.pathfollower.PathFollowerBuilderDsl
import kotlinx.coroutines.*
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
import org.mechdancer.remote.presets.RemoteHub

@BuilderDslMarker
class BusinessBuilderDsl private constructor() {
    private var followerConfig: PathFollowerBuilderDsl.() -> Unit = {}
    var directionLimit: Angle = 180.toDegree()

    var localRadius: Double = .5
    var pathInterval: Double = .05
    var logger: SimpleLogger? = SimpleLogger("Business")
    var painter: RemoteHub? = null

    fun follower(block: PathFollowerBuilderDsl.() -> Unit) {
        followerConfig = block
    }

    companion object {
        fun CoroutineScope.business(
            robotOnMap: ReceiveChannel<Stamped<Odometry>>,
            robotOnOdometry: ReceiveChannel<Stamped<Odometry>>,
            commandOut: SendChannel<NonOmnidirectional>,
            exceptions: SendChannel<ExceptionMessage>,
            block: BusinessBuilderDsl.() -> Unit
        ) = BusinessBuilderDsl()
            .apply(block)
            .apply {
                require(localRadius > 0)
                require(pathInterval > 0)
            }
            .run {
                Business(
                    scope = this@business,
                    robotOnMap = robotOnMap,
                    robotOnOdometry = robotOnOdometry,
                    commandOut = commandOut,
                    exceptions = exceptions,

                    followerConfig = followerConfig,
                    directionLimit = directionLimit,

                    localRadius = localRadius,
                    pathInterval = pathInterval,

                    logger = logger,
                    painter = painter
                )
            }

        @ExperimentalCoroutinesApi
        fun CoroutineScope.startBusiness(
            robotOnMap: ReceiveChannel<Stamped<Odometry>>,
            robotOnOdometry: ReceiveChannel<Stamped<Odometry>>,
            commandOut: SendChannel<NonOmnidirectional>,
            exceptions: SendChannel<ExceptionMessage>,
            consoleParser: Parser,
            block: BusinessBuilderDsl.() -> Unit
        ) {
            val business = business(
                robotOnMap = robotOnMap,
                robotOnOdometry = robotOnOdometry,
                commandOut = commandOut,
                exceptions = exceptions,
                block = block
            )
            with(consoleParser) {
                this["cancel"] = {
                    runBlocking(coroutineContext) { business.cancel() }
                    "current mode: ${business.function?.toString() ?: "Idle"}"
                }
                this["record"] = {
                    runBlocking(coroutineContext) { business.startRecording() }
                    "current mode: ${business.function?.toString() ?: "Idle"}"
                }
                this["clear"] = {
                    (business.function as? Recording)
                        ?.clear()
                        ?.let { "path cleared" }
                    ?: "cannot clear recorded path unless when recording"
                }

                this["save @name"] = {
                    val name = get(1).toString()
                    (business.function as? Recording)
                        ?.save(name)
                        ?.let { "$it nodes were saved in $name" }
                    ?: "cannot save recorded path unless when recording"
                }
                this["refresh @name"] = {
                    runBlocking(coroutineContext) { business.cancel() }

                }
                this["load @name"] = {
                    val name = get(1).toString()
                    business.globals.load(name, .0)
                        ?.let {
                            launch { business.startFollowing(it) }
                            "${it.size} nodes loaded from $name"
                        }
                    ?: "no path named $name"
                }
                this["load @name @num%"] = {
                    val name = get(1).toString()
                    business.globals.load(name, numbers.single() / 100)
                        ?.let {
                            launch { business.startFollowing(it) }
                            "${it.size} nodes loaded from $name"
                        }
                    ?: "no path named $name"
                }
                this["resume @name"] = {
                    val name = get(1).toString()
                    business.globals.resume(name)
                        ?.let {
                            launch { business.startFollowing(it) }
                            "${it.size} nodes loaded from $name"
                        }
                    ?: "no path named $name"
                }

                val formatter = java.text.DecimalFormat("0.00")
                this["progress"] = {
                    (business.function as? Following)
                        ?.let { "progress = ${formatter.format(it.global.progress * 100)}%" }
                    ?: business.globals.toString()
                }

                this["loop on"] = {
                    (business.function as? Following)
                        ?.let { it.loop = true; "loop on" }
                    ?: "cannot set loop unless when following"
                }
                this["loop off"] = {
                    (business.function as? Following)
                        ?.let { it.loop = false; "loop off" }
                    ?: "cannot set loop unless when following"
                }
                this["\'"] = {
                    (business.function as? Following)
                        ?.let {
                            it.isEnabled = !it.isEnabled
                            if (it.isEnabled) "continue" else "pause"
                        }
                    ?: "cannot set enabled unless when following"
                }

                this["function"] = { business.function?.toString() ?: "Idle" }
                this["shutdown"] = { cancel(); "Bye~" }
            }
        }
    }
}
