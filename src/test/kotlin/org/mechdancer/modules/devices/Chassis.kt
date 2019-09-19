package org.mechdancer.modules.devices

import cn.autolabor.Odometry
import cn.autolabor.Stamped
import cn.autolabor.core.server.ServerManager
import cn.autolabor.message.navigation.Msg2DOdometry
import cn.autolabor.message.navigation.Msg2DPose
import cn.autolabor.message.navigation.Msg2DTwist
import cn.autolabor.pm1.sdk.PM1
import cn.autolabor.util.lambda.LambdaFunWithName
import cn.autolabor.util.lambda.function.TaskLambdaFun01
import cn.autolabor.util.reflect.TypeNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.geometry.angle.toRad

/**
 * 底盘设备封装
 */
sealed class Chassis {
    data class Twist(val v: Double, val w: Double)

    protected val poseChannel = Channel<Stamped<Odometry>>(Channel.CONFLATED)
    protected val twistChannel = Channel<Twist>(Channel.CONFLATED)

    val robotPose: ReceiveChannel<Stamped<Odometry>> get() = poseChannel
    val twistCommand: SendChannel<Twist> get() = twistChannel

    /**
     * 框架底盘
     */
    class FrameworkRemoteChassis(scope: CoroutineScope) : Chassis() {
        init {
            val framework = ServerManager.me()

            val cmdvelTopic = framework.getConfig("PM1Task", "cmdvelTopic") as? String ?: "cmdvel"
            val odometryTopic = framework.getConfig("PM1Task", "odometryTopic") as? String ?: "odometry"

            val cmdvel = framework.getOrCreateMessageHandle(cmdvelTopic, TypeNode(Msg2DOdometry::class.java))
            val odometry = framework.getOrCreateMessageHandle(odometryTopic, TypeNode(Msg2DOdometry::class.java))

            odometry.addCallback(LambdaFunWithName(
                "odometry_handel",
                object : TaskLambdaFun01<Msg2DOdometry> {
                    override fun run(p0: Msg2DOdometry?) {
                        val data = p0?.pose ?: return
                        scope.launch {
                            poseChannel.send(Stamped(
                                p0.header.stamp,
                                Odometry(vector2DOf(data.x, data.y), data.yaw.toRad())))
                        }
                    }
                }))
            scope.launch {
                while (isActive) {
                    val (v, w) = twistChannel.receive()
                    cmdvel.pushSubData(Msg2DOdometry(Msg2DPose(), Msg2DTwist(v, .0, w)))
                }
                poseChannel.close()
                twistChannel.close()
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
                while (isActive) {
                    val (_, _, _, x, y, theta) = PM1.odometry
                    poseChannel.send(Stamped.stamp(Odometry(vector2DOf(x, y), theta.toRad())))
                    delay(40L)
                }
                poseChannel.close()
            }
            scope.launch {
                while (isActive) {
                    val (v, w) = twistChannel.receive()
                    PM1.drive(v, w)
                }
                twistChannel.close()
            }
        }
    }
}
