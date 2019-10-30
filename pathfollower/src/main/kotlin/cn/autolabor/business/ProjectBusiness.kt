package cn.autolabor.business

import cn.autolabor.business.ProjectBusiness.Functions
import cn.autolabor.pathfollower.FollowCommand.*
import cn.autolabor.pathfollower.VirtualLightSensorPathFollower
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.mechdancer.SimpleLogger
import org.mechdancer.algebra.function.vector.euclid
import org.mechdancer.algebra.function.vector.minus
import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.common.*
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.exceptions.ExceptionMessage.Occurred
import org.mechdancer.exceptions.ExceptionMessage.Recovered
import org.mechdancer.geometry.angle.Angle
import org.mechdancer.paintPoses
import org.mechdancer.paintVectors
import org.mechdancer.remote.presets.RemoteHub
import java.io.Closeable
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sign

class ProjectBusiness : Business<Functions> {
    override val mode: Functions = Functions.Idle

    sealed class Functions : Closeable {
        object Idle : Functions() {
            override fun close() = Unit
        }

        class Record(
            scope: CoroutineScope,
            private var robotOnMap: ReceiveChannel<Odometry>,
            private val recordInterval: Double
        ) : Functions() {
            private val path = mutableListOf<Odometry>()
            private val lock = ReentrantReadWriteLock()

            private var job = scope.launch {
                for (pose in robotOnMap)
                    path.lastOrNull()
                        .let { it == null || it.p euclid pose.p >= recordInterval }
                        .also { if (it) lock.write { path += pose } }
            }

            fun snapshot() = lock.read { path.toList() }

            override fun close() {
                job.cancel()
            }
        }

        class FollowFunction(
            scope: CoroutineScope,
            private val robotOnMap: ReceiveChannel<Stamped<Odometry>>,
            private val robotOnOdometry: ReceiveChannel<Stamped<Odometry>>,
            private val commandOut: SendChannel<NonOmnidirectional>,
            private val exceptions: SendChannel<ExceptionMessage>,
            private val follower: VirtualLightSensorPathFollower,
            directionLimit: Angle,
            searchCount: Int,
            private val logger: SimpleLogger?,
            private val painter: RemoteHub?
        ) : Functions() {
            private val turnDirectionRad = directionLimit.asRadian()

            private var isEnabled = false
            private var job = scope.launch {
                for ((_, pose) in robotOnMap) {
                    val command = follower(pose)
                    logger?.log(command)
                    if (command !is Error) {
                        exceptions.send(Recovered(FollowFailedException))
                        when (command) {
                            is Follow -> {
                                val (v, w) = command
                                drive(v, w)
                            }
                            is Turn   -> {
                                val (angle) = command
                                stop()
                                turn(angle)
                                stop()
                            }
                            is Finish ->
                                stop()
                        }
                    } else
                        exceptions.send(Occurred(FollowFailedException))
                    painter?.run {
                        val robotToMap = pose.toTransformation()
                        follower.sensor.area
                            .takeIf(Collection<*>::isNotEmpty)
                            ?.map { robotToMap(it).to2D() }
                            ?.let { it + it.first() }
                            ?.also { paintVectors("传感器区域", it) }
                        paintPoses("尖点", listOf(robotToMap(follower.tip)))
                    }
                }
            }

            override fun close() {
                job.cancel()
            }

            private suspend fun drive(v: Number, w: Number) {
                if (isEnabled) commandOut.send(Velocity.velocity(v, w))
            }

            private suspend fun stop() {
                commandOut.send(Velocity.velocity(.0, .0))
            }

            @Suppress("unused")
            private suspend fun goStraight(distance: Double) {
                val (p0, _) = robotOnOdometry.receive().data
                for ((_, pose) in robotOnOdometry) {
                    if ((pose.p - p0).norm() > distance) break
                    drive(.1, 0)
                }
            }

            private suspend fun turn(angle: Double) {
                val d0 = robotOnOdometry.receive().data.d.asRadian()
                val value = when (turnDirectionRad) {
                    in angle..0.0 -> angle + 2 * PI
                    in 0.0..angle -> angle - 2 * PI
                    else          -> angle
                }
                val delta = abs(value)
                val w = value.sign * follower.maxOmegaRad
                for ((_, pose) in robotOnOdometry) {
                    if (abs(pose.d.asRadian() - d0) > delta) break
                    drive(0, w)
                }
            }
        }
    }
}
