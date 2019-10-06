package cn.autolabor.pathfollower

import cn.autolabor.pathfollower.Mode.Idle
import cn.autolabor.pathfollower.Mode.Record
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
    private val commandOut: SendChannel<NonOmnidirectional>,
    private val follower: VirtualLightSensorPathFollower,
    private val directionLimit: Double,
    pathInterval: Double,
    val logger: SimpleLogger?,
    val painter: RemoteHub?
) {
    val path = PathManager(pathInterval)

    init {
        logger?.period = 1
    }

    private var internalMode: Mode = Idle
        set(value) {
            field = value
            logger?.log("mode = $value")
        }

    var mode
        get() = internalMode
        set(value) {
            when (internalMode) {
                Record         -> when (value) {
                    Record         -> Unit
                    is Mode.Follow -> Unit
                    Idle           -> internalMode = Idle
                }
                is Mode.Follow -> when (value) {
                    Record         -> Unit
                    is Mode.Follow -> Unit
                    Idle           -> internalMode = Idle
                }
                Idle           -> when (value) {
                    Record         -> {
                        internalMode = Record
                        scope.launch { record() }
                    }
                    is Mode.Follow -> {
                        if (path.size < 2) return

                        follower.setPath(path.get())
                        isEnabled = false

                        internalMode = value
                        scope.launch { follow() }
                    }
                    Idle           -> Unit
                }
            }
        }

    var isEnabled = false
        set(value) {
            if (internalMode is Mode.Follow) field = value
        }

    private suspend fun record() {
        for ((_, pose) in robotOnMap) {
            if (internalMode != Record) break
            path.record(pose)
        }
    }

    private suspend fun follow() {
        for ((_, pose) in robotOnMap) {
            val m = internalMode
            if (m !is Mode.Follow) break
            val command = follower(pose)
            logger?.log(command)
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
                is Error  -> Unit
                is Finish -> {
                    if (m.loop)
                        follower.setPath(path.get())
                    else {
                        stop()
                        internalMode = Idle
                    }
                }
            }
            painter?.run {
                paintVectors("传感器区域", follower.sensor.areaShape)
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
        val (p0, _) = robotOnMap.receive().data
        for ((_, pose) in robotOnMap) {
            if ((pose.p - p0).norm() > distance) break
            drive(.1, 0)
        }
    }

    private suspend fun turn(angle: Double) {
        val (_, d0) = robotOnMap.receive().data
        val value = angle.let {
            when {
                directionLimit < 0 && it < directionLimit -> it + 2 * PI
                directionLimit > 0 && it > directionLimit -> it - 2 * PI
                else                                      -> it
            }
        }
        val delta = abs(value)
        val w = value.sign * follower.maxAngularSpeed
        for ((_, pose) in robotOnMap) {
            if (abs(pose.d.asRadian() - d0.asRadian()) > delta) break
            drive(0, w)
        }
    }
}
