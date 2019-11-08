package cn.autolabor.business

import cn.autolabor.pathfollower.FollowCommand
import cn.autolabor.pathfollower.PathFollowerBuilderDsl
import cn.autolabor.pathfollower.PathFollowerBuilderDsl.Companion.pathFollower
import cn.autolabor.pathfollower.VirtualLightSensorPathFollower
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.mechdancer.SimpleLogger
import org.mechdancer.algebra.function.vector.euclid
import org.mechdancer.algebra.function.vector.minus
import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.common.*
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.geometry.angle.Angle
import org.mechdancer.paintPoses
import org.mechdancer.paintVectors
import org.mechdancer.remote.presets.RemoteHub
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sign

class Business(
    private val scope: CoroutineScope,
    private val robotOnMap: ReceiveChannel<Stamped<Odometry>>,
    private val robotOnOdometry: ReceiveChannel<Stamped<Odometry>>,
    private val commandOut: SendChannel<Velocity.NonOmnidirectional>,
    private val exceptions: SendChannel<ExceptionMessage>,

    private val followerConfig: PathFollowerBuilderDsl.() -> Unit,
    private val directionLimit: Angle,

    localRadius: Double,
    private val pathInterval: Double,
    localFirst: (Odometry) -> Boolean,
    localPlanner: (Sequence<Odometry>) -> Sequence<Odometry>,

    private val logger: SimpleLogger?,
    private val painter: RemoteHub?
) {
    var function: Functions? = null
        private set

    val globals = PathManager(localRadius, pathInterval, localFirst, localPlanner, logger, painter)

    suspend fun startRecording() {
        if (function is Functions.Recording) return
        function?.job?.cancelAndJoin()
        function = Functions.Recording(
                scope,
                robotOnMap,
                globals,
                pathInterval)
    }

    suspend fun startFollowing(global: GlobalPath) {
        if (function is Functions.Following) return
        function?.job?.cancelAndJoin()
        function = Functions.Following(
                scope,
                robotOnMap,
                robotOnOdometry,
                commandOut,
                exceptions,

                pathFollower(global, followerConfig),
                directionLimit,

                logger,
                painter)
    }

    suspend fun cancel() {
        function?.job?.cancelAndJoin()
        function = null
    }

    sealed class Functions {
        internal abstract val job: Job
        override fun toString(): String = javaClass.simpleName

        class Recording(
            scope: CoroutineScope,
            robotOnMap: ReceiveChannel<Stamped<Odometry>>,
            private val globals: PathManager,
            pathInterval: Double
        ) : Functions() {
            private val list = mutableListOf<Odometry>()
            override val job = scope.launch {
                for ((_, pose) in robotOnMap)
                    synchronized(list) {
                        list.lastOrNull()
                            .let { it == null || it.p euclid pose.p > pathInterval }
                            .also { if (it) list += pose }
                    }
            }

            fun save(fileName: String): Int {
                val copy = synchronized(list) { list.toList() }
                globals.save(fileName, copy)
                return copy.size
            }

            fun clear() {
                synchronized(list) { list.clear() }
            }
        }

        class Following(
            scope: CoroutineScope,
            private val robotOnMap: ReceiveChannel<Stamped<Odometry>>,
            private val robotOnOdometry: ReceiveChannel<Stamped<Odometry>>,
            private val commandOut: SendChannel<Velocity.NonOmnidirectional>,
            private val exceptions: SendChannel<ExceptionMessage>,

            private val follower: VirtualLightSensorPathFollower,
            directionLimit: Angle,

            val logger: SimpleLogger?,
            val painter: RemoteHub?
        ) : Functions() {
            private val turnDirectionRad = directionLimit.asRadian()

            val global = follower.global
            var isEnabled = false
            var loop = false

            override val job = scope.launch {
                `for`@ for ((_, pose) in robotOnMap) {
                    val command = follower(pose)
                    logger?.log(command)
                    if (command !is FollowCommand.Error) {
                        exceptions.send(ExceptionMessage.Recovered(FollowFailedException))
                        when (command) {
                            is FollowCommand.Follow -> {
                                val (v, w) = command
                                drive(v, w)
                            }
                            is FollowCommand.Turn   -> {
                                val (angle) = command
                                stop()
                                turn(follower.maxOmegaRad, angle)
                                stop()
                            }
                            is FollowCommand.Finish -> {
                                if (loop)
                                    follower.global.progress = .0
                                else {
                                    stop()
                                    break@`for`
                                }
                            }
                        }
                    } else
                        exceptions.send(ExceptionMessage.Occurred(FollowFailedException))
                    painter?.run {
                        val robotToMap = pose.toTransformation()
                        follower.sensor.area
                            .takeIf(Collection<*>::isNotEmpty)
                            ?.map { robotToMap(it).to2D() }
                            ?.let { it + it.first() }
                            ?.also { paintVectors("传感器区域", it) }
                        paintPoses("尖点", listOf(robotToMap.transform(follower.tip)))
                    }
                }
            }

            private suspend fun drive(v: Number, w: Number) {
                if (isEnabled) commandOut.send(Velocity.velocity(v, w))
            }

            private suspend fun stop() {
                commandOut.send(Velocity.velocity(.0, .0))
            }

            @Suppress("unused")
            private suspend fun goStraight(v: Double, distance: Double) {
                val (p0, _) = robotOnOdometry.receive().data
                for ((_, pose) in robotOnOdometry) {
                    if ((pose.p - p0).norm() > distance) break
                    drive(v, 0)
                }
            }

            private suspend fun turn(omega: Double, angle: Double) {
                val d0 = robotOnOdometry.receive().data.d.asRadian()
                val value = when (turnDirectionRad) {
                    in angle..0.0 -> angle + 2 * PI
                    in 0.0..angle -> angle - 2 * PI
                    else          -> angle
                }
                val delta = abs(value)
                val w = value.sign * omega
                for ((_, pose) in robotOnOdometry) {
                    if (abs(pose.d.asRadian() - d0) > delta) break
                    drive(0, w)
                }
            }
        }
    }
}
