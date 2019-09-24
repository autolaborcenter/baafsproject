package org.mechdancer

import cn.autolabor.core.server.ServerManager
import cn.autolabor.message.navigation.Msg2DOdometry
import cn.autolabor.message.navigation.Msg2DTwist
import cn.autolabor.util.lambda.LambdaFunWithName
import cn.autolabor.util.lambda.function.TaskLambdaFun01
import cn.autolabor.util.reflect.TypeNode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity.Companion.velocity
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.modules.devices.Chassis.PM1Chassis
import org.mechdancer.modules.devices.Locator.FrameworkRemoteLocator.MarvelmindLocator
import org.mechdancer.modules.obstacleDetecting
import org.mechdancer.modules.startLocationFilter
import org.mechdancer.modules.startPathFollower

fun main() = runBlocking<Unit> {
    // 话题
    val robotOnMap = Channel<Stamped<Odometry>>(Channel.CONFLATED)
    // 模块
    val locator = MarvelmindLocator(this)
    val chassis = PM1Chassis(this)
    // 障碍物检测
    obstacleDetecting()
    val toObstacle = Channel<NonOmnidirectional>(Channel.CONFLATED)
    // 任务
    startLocationFilter(
        robotOnLocator = locator.robotLocation,
        robotOnOdometry = chassis.robotPose,
        robotOnMap = robotOnMap)
    startPathFollower(
        robotOnMap = robotOnMap,
        twistCommand = toObstacle)
    // 转发
    launch {
        val toObstacleTopic =
            ServerManager.me().getOrCreateMessageHandle("cmdvel_in", TypeNode(Msg2DOdometry::class.java))
        for (v in toObstacle)
            toObstacleTopic.pushSubData(Msg2DOdometry(null, Msg2DTwist(v.v, .0, v.w)))
    }
    val fromObstacleTopic =
        ServerManager.me().getOrCreateMessageHandle("cmdvel_out", TypeNode(Msg2DOdometry::class.java))
    fromObstacleTopic.addCallback(LambdaFunWithName("chassis", object : TaskLambdaFun01<Msg2DOdometry> {
        override fun run(p0: Msg2DOdometry?) {
            val v = p0?.twist ?: return
            launch { chassis.twistCommand.send(velocity(v.x, v.yaw)) }
        }
    }))
}

