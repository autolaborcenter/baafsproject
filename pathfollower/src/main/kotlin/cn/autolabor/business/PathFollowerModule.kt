package cn.autolabor.business

import cn.autolabor.pathfollower.FollowCommand.*
import cn.autolabor.pathfollower.VirtualLightSensorPathFollower
import cn.autolabor.pathmaneger.FixedDistancePathRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.mechdancer.SimpleLogger
import org.mechdancer.algebra.function.vector.minus
import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity.Companion.velocity
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.common.invoke
import org.mechdancer.common.toTransformation
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.exceptions.ExceptionMessage.Occurred
import org.mechdancer.exceptions.ExceptionMessage.Recovered
import org.mechdancer.geometry.angle.Angle
import org.mechdancer.paintPoses
import org.mechdancer.paintVectors
import org.mechdancer.remote.presets.RemoteHub
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * 循径模块
 * 处理循径相关一切功能
 */
class PathFollowerModule(
    private val scope: CoroutineScope,
    private val robotOnMap: ReceiveChannel<Stamped<Odometry>>,
    private val robotOnOdometry: ReceiveChannel<Stamped<Odometry>>,
    private val commandOut: SendChannel<NonOmnidirectional>,
    private val exceptions: SendChannel<ExceptionMessage<FollowFailedException>>,
    private val follower: VirtualLightSensorPathFollower,
    directionLimit: Angle,
    pathInterval: Double,
    searchLength: Double,
    val logger: SimpleLogger?,
    val painter: RemoteHub?
) {
    val path = FixedDistancePathRecorder(pathInterval)

    private val turnDirectionRad = directionLimit.asRadian()
    private val searchCount = (searchLength / pathInterval).roundToInt()

    private var internalMode: Business = Business.Idle
        set(value) {
            field = value
            logger?.log("mode = $value")
        }

    var progress: Double
        get() =
            onFollowing("progress") { follower.progress }
        set(value) {
            onFollowing("progress") { follower.progress = value }
        }

    var isEnabled = false
        get() =
            onFollowing("enabled") { field }
        set(value) {
            onFollowing("enabled") { field = value }
        }

    private fun <T> onFollowing(what: String, block: () -> T): T {
        if (internalMode !is Business.Follow)
            throw RuntimeException("cannot get or set $what unless in follow mode")
        return block()
    }

    var mode
        get() = internalMode
        set(value) {
            when (internalMode) {
                Business.Record    -> when (value) {
                    Business.Record,
                    is Business.Follow -> Unit
                    Business.Idle      -> internalMode = value
                }
                is Business.Follow -> when (value) {
                    Business.Record -> Unit
                    is Business.Follow,
                    Business.Idle   -> internalMode = value
                }
                Business.Idle      -> when (value) {
                    Business.Record    -> {
                        internalMode = value
                        scope.launch { record() }
                    }
                    is Business.Follow -> {
                        if (path.size < 2) return
                        loadPath()
                        internalMode = value
                        scope.launch { follow() }
                    }
                    Business.Idle      -> Unit
                }
            }
        }

    private suspend fun record() {
        for ((_, pose) in robotOnMap) {
            if (internalMode != Business.Record) break
            path.record(pose)
        }
    }

    private fun loadPath() {
        follower.setPath(path.toGlobalPath(.5, searchCount))
    }

    private suspend fun follow() {
        isEnabled = false
        for ((_, pose) in robotOnMap) {
            val m = internalMode
            if (m !is Business.Follow) break
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
                    is Finish -> {
                        if (m.loop)
                            loadPath()
                        else {
                            stop()
                            internalMode = Business.Idle
                        }
                    }
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

    private suspend fun drive(v: Number, w: Number) {
        if (isEnabled) commandOut.send(velocity(v, w))
    }

    private suspend fun stop() {
        commandOut.send(velocity(.0, .0))
    }

    @Suppress("unused")
    private suspend fun goStraight(distance: Double) {
        val (p0, _) = robotOnOdometry.receive().data
        for ((_, pose) in robotOnOdometry) {
            if (internalMode !is Business.Follow) break
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
            if (internalMode !is Business.Follow) break
            if (abs(pose.d.asRadian() - d0) > delta) break
            drive(0, w)
        }
    }
}
