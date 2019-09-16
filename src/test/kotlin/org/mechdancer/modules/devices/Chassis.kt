package org.mechdancer.modules.devices

import cn.autolabor.Odometry
import cn.autolabor.Stamped
import cn.autolabor.core.server.ServerManager
import cn.autolabor.message.navigation.Msg2DOdometry
import cn.autolabor.message.navigation.Msg2DPose
import cn.autolabor.message.navigation.Msg2DTwist
import cn.autolabor.pm1.sdk.PM1
import cn.autolabor.util.reflect.TypeNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.geometry.angle.toRad
import java.io.Closeable

/**
 * 底盘设备封装
 */
sealed class Chassis : Closeable {
    protected val poseChannel = Channel<Stamped<Odometry>>(Channel.CONFLATED)
    protected val twistChannel = Channel<Twist>(Channel.CONFLATED)
    protected var running = true

    val robotPose: ReceiveChannel<Stamped<Odometry>> get() = poseChannel
    val twistCommand: SendChannel<Twist> get() = twistChannel

    override fun close() {
        running = false
    }

    /**
     * 框架底盘
     */
    class FrameworkRemoteChassis(scope: CoroutineScope) : Chassis() {
        init {
            val framework = ServerManager.me()

            val cmdvelTopic = framework.getConfig("PM1Task", "cmdvelTopic") as? String ?: "cmdvel"
            val odometryTopic = framework.getConfig("PM1Task", "odometryTopic") as? String ?: "odometry"

            val handle = framework.getOrCreateMessageHandle(cmdvelTopic, TypeNode(Msg2DOdometry::class.java))
            val odometry = framework.getOrCreateMessageHandle(odometryTopic, TypeNode(Msg2DOdometry::class.java))
            scope.launch {
                while (running) {
                    val temp = odometry.firstData as? Msg2DOdometry ?: continue
                    val data = temp.pose
                    poseChannel.send(Stamped(temp.header.stamp, Odometry(vector2DOf(data.x, data.y), data.yaw.toRad())))
                    delay(30L)
                }
            }
            scope.launch {
                while (running) {
                    val (v, w) = twistChannel.receive()
                    handle.pushSubData(Msg2DOdometry(Msg2DPose(), Msg2DTwist(v, .0, w)))
                }
            }
        }
    }

    /**
     * PM1 真车
     */
    class PM1Chassis(scope: CoroutineScope) : Chassis() {
        init {
            PM1.initialize()
            PM1.locked = false
            scope.launch {
                while (running) {
                    val (_, _, _, x, y, theta) = PM1.odometry
                    poseChannel.send(Stamped.stamp(Odometry(vector2DOf(x, y), theta.toRad())))
                    delay(40L)
                }
            }
            scope.launch {
                while (running) {
                    val (v, w) = twistChannel.receive()
                    PM1.drive(v, w)
                }
            }
        }
    }
}
