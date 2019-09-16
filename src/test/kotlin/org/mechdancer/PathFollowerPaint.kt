package org.mechdancer

import cn.autolabor.Odometry
import cn.autolabor.Stamped
import cn.autolabor.core.server.ServerManager
import cn.autolabor.locator.ParticleFilterBuilder
import cn.autolabor.message.navigation.Msg2DOdometry
import cn.autolabor.message.navigation.Msg2DPose
import cn.autolabor.message.navigation.Msg2DTwist
import cn.autolabor.util.reflect.TypeNode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.modules.PathFollowerModule
import org.mechdancer.modules.PathFollowerModule.Twist

fun main() = runBlocking {
    val handle = ServerManager.me().getOrCreateMessageHandle("cmdvel", TypeNode(Msg2DOdometry::class.java))
    val locate = ServerManager.me().getOrCreateMessageHandle("abs", TypeNode(Msg2DOdometry::class.java))
    ServerManager.me().dump()

    // 粒子滤波器
    val filter = ParticleFilterBuilder.particleFilter { locatorOnRobot = vector2DOf(-0.31, 0) }
    // 消息通道
    val robotOnMap = channel<Stamped<Odometry>>()
    val twistCommand = channel<Twist>()
    launch {
        while (true) {
            delay(30L)
            val temp = locate.firstData as? Msg2DOdometry ?: continue
            val data = temp.pose
            robotOnMap.send(Stamped(temp.header.stamp, Odometry(vector2DOf(data.x, data.y), data.yaw.toRad())))
        }
    }
    launch {
        while (true) {
            twistCommand.receive()
                .let { (v, w) ->
                    handle.pushSubData(Msg2DOdometry(Msg2DPose(), Msg2DTwist(v, .0, w)))
                }
        }
    }
    // 导航模块
    PathFollowerModule(
        robotOnMap = robotOnMap,
        twistChannel = twistCommand
    ).use { it.parseRepeatedly() }
}
