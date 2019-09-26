package org.mechdancer.modules

import cn.autolabor.core.server.ServerManager
import cn.autolabor.message.navigation.Msg2DOdometry
import cn.autolabor.message.navigation.Msg2DPose
import cn.autolabor.message.navigation.Msg2DTwist
import cn.autolabor.pm1.sdk.PM1
import cn.autolabor.util.lambda.LambdaFunWithName
import cn.autolabor.util.lambda.function.TaskLambdaFun01
import cn.autolabor.util.reflect.TypeNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Stamped.Companion.stamp
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.modules.LinkMode.Direct
import org.mechdancer.modules.LinkMode.Framework

/** 以 [mode] 模式启动底盘 */
fun CoroutineScope.startChassis(
    mode: LinkMode,
    odometry: SendChannel<Stamped<Odometry>>,
    command: ReceiveChannel<NonOmnidirectional>
) {
    when (mode) {
        Direct    -> {
            PM1.initialize()
            PM1.setCommandEnabled(false)
            PM1.locked = false
            launch {
                while (isActive) {
                    val (_, _, _, x, y, theta) = PM1.odometry
                    odometry.send(stamp(Odometry.odometry(x, y, theta)))
                    delay(30L)
                }
                odometry.close()
            }
            launch { for ((v, w) in command) PM1.drive(v, w) }
        }
        Framework -> {
            with(ServerManager.me()) {
                getOrCreateMessageHandle(
                    getConfig("PM1Task", "odometryTopic") as? String ?: "odometry",
                    TypeNode(Msg2DOdometry::class.java))
                    .addCallback(LambdaFunWithName(
                        "odometry_handel",
                        object : TaskLambdaFun01<Msg2DOdometry> {
                            override fun run(p0: Msg2DOdometry?) {
                                val data = p0?.pose ?: return
                                launch {
                                    odometry.send(Stamped(p0.header.stamp, Odometry.odometry(data.x, data.y, data.yaw)))
                                }
                            }
                        }))
                launch {
                    val cmdvel = getOrCreateMessageHandle(
                        getConfig("PM1Task", "cmdvelTopic") as? String ?: "cmdvel",
                        TypeNode(Msg2DOdometry::class.java))
                    for ((v, w) in command) cmdvel.pushSubData(Msg2DOdometry(Msg2DPose(), Msg2DTwist(v, .0, w)))
                }
            }
        }
    }
}
