package org.mechdancer.modules

import cn.autolabor.core.server.ServerManager
import cn.autolabor.message.navigation.Msg2DOdometry
import cn.autolabor.message.navigation.Msg2DTwist
import cn.autolabor.util.lambda.LambdaFunWithName
import cn.autolabor.util.lambda.function.TaskLambdaFun01
import cn.autolabor.util.reflect.TypeNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.mechdancer.common.Velocity
import org.mechdancer.common.Velocity.NonOmnidirectional

class Obstacle(scope: CoroutineScope, twistCommand: SendChannel<NonOmnidirectional>) {
    val toObstacle = Channel<NonOmnidirectional>(Channel.CONFLATED)

    init {
        obstacleDetecting()
        scope.launch {
            val toObstacleTopic =
                ServerManager.me().getOrCreateMessageHandle("cmdvel_in", TypeNode(Msg2DOdometry::class.java))
            for (v in toObstacle)
                toObstacleTopic.pushSubData(Msg2DOdometry(null, Msg2DTwist(v.v, .0, v.w)))
        }
        val fromObstacleTopic =
            ServerManager.me().getOrCreateMessageHandle("cmdvel", TypeNode(Msg2DOdometry::class.java))
        fromObstacleTopic.addCallback(LambdaFunWithName("chassis", object : TaskLambdaFun01<Msg2DOdometry> {
            override fun run(p0: Msg2DOdometry?) {
                val v = p0?.twist ?: return
                scope.launch { twistCommand.send(Velocity.velocity(v.x, v.yaw)) }
            }
        }))
    }
}