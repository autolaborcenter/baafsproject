package cn.autolabor.business

import cn.autolabor.business.Business.Idle
import cn.autolabor.business.Business.Record
import cn.autolabor.pathfollower.algorithm.FollowCommand.*
import cn.autolabor.pathfollower.algorithm.VirtualLightSensorPathFollower
import cn.autolabor.pathmaneger.PathManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.mechdancer.SimpleLogger
import org.mechdancer.algebra.function.vector.minus
import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity.Companion.velocity
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.exceptions.ExceptionMessage.Occurred
import org.mechdancer.exceptions.ExceptionMessage.Recovered
import org.mechdancer.geometry.angle.Angle
import org.mechdancer.paintPoses
import org.mechdancer.paintVectors
import org.mechdancer.remote.presets.RemoteHub
import kotlin.math.PI
import kotlin.math.abs
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
    val logger: SimpleLogger?,
    val painter: RemoteHub?
) {
    val path = PathManager(pathInterval)
    private val turnDirection = directionLimit.asRadian()

    private var internalBusiness: Business = Idle
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
        if (internalBusiness !is Business.Follow)
            throw RuntimeException("cannot get or set $what unless in follow mode")
        return block()
    }

    var mode
        get() = internalBusiness
        set(value) {
            when (internalBusiness) {
                Record             -> when (value) {
                    Record             -> Unit
                    is Business.Follow -> Unit
                    Idle               -> internalBusiness = Idle
                }
                is Business.Follow -> when (value) {
                    Record -> Unit
                    is Business.Follow,
                    Idle   -> internalBusiness = value
                }
                Idle               -> when (value) {
                    Record             -> {
                        internalBusiness = Record
                        scope.launch { record() }
                    }
                    is Business.Follow -> {
                        if (path.size < 2) return

                        follower.setPath(path.get())

                        internalBusiness = value
                        scope.launch { follow() }
                    }
                    Idle               -> Unit
                }
            }
        }

    private suspend fun record() {
        for ((_, pose) in robotOnMap) {
            if (internalBusiness != Record) break
            path.record(pose)
        }
    }

    private suspend fun follow() {
        isEnabled = false
        for ((_, pose) in robotOnMap) {
            val m = internalBusiness
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
                            follower.setPath(path.get())
                        else {
                            stop()
                            internalBusiness = Idle
                        }
                    }
                }
            } else
                exceptions.send(Occurred(FollowFailedException))
            painter?.run {
                follower.sensor.areaShape
                    .takeIf(Collection<*>::isNotEmpty)
                    ?.let { it + it.first() }
                    ?.also { paintVectors("传感器区域", it) }
                paintPoses("尖点", listOf(follower.tip))
            }
        }
    }

    private suspend fun drive(v: Number, w: Number) {
        if (isEnabled) commandOut.send(velocity(v, w))
    }

    private suspend fun stop() {
        commandOut.send(velocity(.0, .0))
    }

    private suspend fun goStraight(distance: Double) {
        val (p0, _) = robotOnOdometry.receive().data
        for ((_, pose) in robotOnOdometry) {
            if (internalBusiness !is Business.Follow) break
            if ((pose.p - p0).norm() > distance) break
            drive(.1, 0)
        }
    }

    private suspend fun turn(angle: Double) {
        val d0 = robotOnOdometry.receive().data.d.asRadian()
        val value = when (turnDirection) {
            in angle..0.0 -> angle + 2 * PI
            in 0.0..angle -> angle - 2 * PI
            else          -> angle
        }
        val delta = abs(value)
        val w = value.sign * follower.maxOmegaRad
        for ((_, pose) in robotOnOdometry) {
            if (internalBusiness !is Business.Follow) break
            if (abs(pose.d.asRadian() - d0) > delta) break
            drive(0, w)
        }
    }
}
