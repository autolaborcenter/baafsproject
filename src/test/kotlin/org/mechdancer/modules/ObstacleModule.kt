package org.mechdancer.modules

import cn.autolabor.core.server.ServerManager
import cn.autolabor.message.navigation.Msg2DOdometry
import cn.autolabor.message.navigation.Msg2DTwist
import cn.autolabor.util.lambda.LambdaFunWithName
import cn.autolabor.util.lambda.function.TaskLambdaFun01
import cn.autolabor.util.reflect.TypeNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.mechdancer.common.Velocity
import org.mechdancer.common.Velocity.NonOmnidirectional

@ExperimentalCoroutinesApi
fun CoroutineScope.startObstacleAvoiding(
    commandIn: ReceiveChannel<NonOmnidirectional>,
    commandOut: SendChannel<NonOmnidirectional>
) {
    obstacleDetecting()
    launch {
        val toObstacleTopic =
            ServerManager.me().getOrCreateMessageHandle("cmdvel_in", TypeNode(Msg2DOdometry::class.java))
        for (v in commandIn)
            toObstacleTopic.pushSubData(Msg2DOdometry(null, Msg2DTwist(v.v, .0, v.w)))
        commandOut.close()
    }
    val fromObstacleTopic =
        ServerManager.me().getOrCreateMessageHandle("cmdvel", TypeNode(Msg2DOdometry::class.java))
    fromObstacleTopic.addCallback(LambdaFunWithName("chassis", object : TaskLambdaFun01<Msg2DOdometry> {
        override fun run(p0: Msg2DOdometry?) {
            if (commandOut.isClosedForSend) return
            val v = p0?.twist ?: return
            launch { commandOut.send(Velocity.velocity(v.x, v.yaw)) }
        }
    }))
}
