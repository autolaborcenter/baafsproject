package cn.autolabor.pathfollower

import cn.autolabor.pathfollower.Mode.Idle
import cn.autolabor.pathfollower.Mode.Record
import cn.autolabor.pathfollower.algorithm.FollowCommand.*
import cn.autolabor.pathfollower.algorithm.PathFollowerBuilderDsl.Companion.pathFollower
import cn.autolabor.pathfollower.algorithm.VirtualLightSensorPathFollower
import cn.autolabor.pathfollower.shape.Circle
import cn.autolabor.pathmaneger.PathManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.mechdancer.common.Odometry
import org.mechdancer.common.Odometry.Companion.odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity.Companion.velocity
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.console.parser.buildParser
import org.mechdancer.console.parser.display
import org.mechdancer.console.parser.feedback
import org.mechdancer.paintFrame3
import org.mechdancer.paintPoses
import org.mechdancer.remote.presets.RemoteHub
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sign

class PathFollowerModule(
    private val scope: CoroutineScope,
    private val robotOnMap: ReceiveChannel<Stamped<Odometry>>,
    private val commandOut: SendChannel<NonOmnidirectional>,
    val path: PathManager,
    private val follower: VirtualLightSensorPathFollower,
    private val directionLimit: Double,
    private val w: Double
) {
    private var internalMode = Idle
    var mode
        get() = internalMode
        set(value) {
            when (internalMode) {
                Record      -> when (value) {
                    Record      -> Unit
                    Mode.Follow -> Unit
                    Idle        -> internalMode = Idle
                }
                Mode.Follow -> when (value) {
                    Record      -> Unit
                    Mode.Follow -> Unit
                    Idle        -> internalMode = Idle
                }
                Idle        -> when (value) {
                    Record      -> {
                        internalMode = Mode.Follow
                        scope.launch { while (internalMode == Record) path.record(robotOnMap.receive().data) }
                    }
                    Mode.Follow -> {
                        require(path.size > 1) { "No path." }

                        follower.setPath(path.get())
                        isEnabled = false

                        internalMode = Mode.Follow
                        scope.launch { while (internalMode == Mode.Follow) follow() }
                    }
                    Idle        -> Unit
                }
            }
        }

    var isEnabled = false
        set(value) {
            if (internalMode == Mode.Follow) field = value
        }

    private suspend fun follow(): Unit =
        when (val command = follower(robotOnMap.receive().data)) {
            is Follow -> {
                val (v, w) = command
                if (isEnabled) commandOut.send(velocity(v, w))
                Unit
            }
            is Turn   -> {
                commandOut.send(velocity(.0, .0))
                delay(200L)
                val (_, d0) = robotOnMap.receive().data
//              while (true) {
//                  if (enabled) commandOut.send(velocity(.1, 0))
//                  val (p, _) = robotOnMap.receive().data
//                  if ((p - p0).norm() > .03) break
//              }
                val angle = command.angle.let {
                    when {
                        directionLimit < 0 && it < directionLimit -> it + 2 * PI
                        directionLimit > 0 && it > directionLimit -> it - 2 * PI
                        else                                      -> it
                    }
                }
                val delta = abs(angle)
                val w = angle.sign * this.w
                while (true) {
                    if (isEnabled) commandOut.send(velocity(.0, w))
                    val (_, d) = robotOnMap.receive().data
                    if (abs(d.asRadian() - d0.asRadian()) > delta) break
                }
            }
            is Error  -> Unit
            is Finish -> {
                commandOut.send(velocity(.0, .0))
                internalMode = Idle
            }
        }

    companion object {
        /**
         * 循径模块
         *
         * 包含任务控制、路径管理、控制台解析、循径任务；
         */
        @ExperimentalCoroutinesApi
        fun CoroutineScope.startPathFollower(
            robotOnMap: ReceiveChannel<Stamped<Odometry>>,
            commandOut: SendChannel<NonOmnidirectional>,
            remote: RemoteHub? = null
        ) {
            val file = File("path.txt")

            val module = PathFollowerModule(
                this,
                robotOnMap,
                commandOut,
                PathManager(interval = 0.05),
                pathFollower {
                    sensorPose = odometry(0.27, 0)
                    lightRange = Circle(radius = 0.3, vertexCount = 64)
                    minTipAngle = PI / 3
                    minTurnAngle = PI / 12
                    maxJumpCount = 20
                },
                -2 * PI / 3,
                PI / 10)

            val parser = buildParser {
                this["record"] = {
                    module.mode = Record
                    "current mode: ${module.mode}"
                }
                this["clear"] = {
                    module.path.clear()
                    remote?.paintFrame3("path", emptyList())
                    "path cleared"
                }
                this["show"] = {
                    with(module.path.get()) {
                        remote?.paintPoses("路径", this)
                        "path count = ${size}\n${joinToString("\n")}"
                    }
                }

                this["save"] = {
                    with(module.path) {
                        saveTo(file)
                        remote?.paintPoses("路径", get())
                        "$size nodes saved"
                    }
                }
                this["load"] = {
                    module.mode = Idle
                    with(module.path) {
                        loadFrom(file)
                        remote?.paintPoses("路径", get())
                        "$size nodes loaded"
                    }
                }
                this["delete"] = { file.writeText(""); "path save deleted" }

                this["go"] = {
                    module.mode = Mode.Follow
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
                while (!robotOnMap.isClosedForReceive)
                    withContext(coroutineContext) { readLine() }
                        ?.let(parser::invoke)
                        ?.map(::feedback)
                        ?.forEach(::display)
            }
        }
    }
}
