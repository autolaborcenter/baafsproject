package org.mechdancer.modules

import cn.autolabor.FilterTwistTask
import cn.autolabor.ObstacleDetectionTask
import cn.autolabor.PoseDetectionTask
import cn.autolabor.baafs.FaselaseTask
import cn.autolabor.baafs.LaserFilterTask
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
import org.mechdancer.modules.LinkMode.Direct

@ExperimentalCoroutinesApi
fun CoroutineScope.startObstacleAvoiding(
    mode: LinkMode,
    commandIn: ReceiveChannel<NonOmnidirectional>,
    commandOut: SendChannel<NonOmnidirectional>
): Unit = with(ServerManager.me()) {
    loadConfig("conf/obstacle.conf")
    if (mode == Direct) {
        register(FaselaseTask("FaselaseTaskFront"))
        register(LaserFilterTask("LaserFilterFront"))
        register(FaselaseTask("FaselaseTaskBack"))
        register(LaserFilterTask("LaserFilterBack"))
    }
    register(ObstacleDetectionTask("ObstacleDetectionTask"))
    register(PoseDetectionTask("PoseDetectionTask"))
    register(FilterTwistTask("FilterTwistTask"))
    getOrCreateMessageHandle(
        getConfig("FilterTwistTask", "cmdTopicOutput") as? String ?: "cmdvel",
        TypeNode(Msg2DOdometry::class.java)
    ).addCallback(LambdaFunWithName("chassis", object : TaskLambdaFun01<Msg2DOdometry> {
        override fun run(p0: Msg2DOdometry?) {
            if (commandOut.isClosedForSend) return
            val v = p0?.twist ?: return
            launch { commandOut.send(Velocity.velocity(v.x, v.yaw)) }
        }
    }))
    getOrCreateMessageHandle(
        getConfig("FilterTwistTask", "cmdTopicInput") as? String ?: "cmdvel_in",
        TypeNode(Msg2DOdometry::class.java)
    ).let { topic ->
        launch {
            for (v in commandIn)
                topic.pushSubData(Msg2DOdometry(null, Msg2DTwist(v.v, .0, v.w)))
            commandOut.close()
        }
    }
    dump()
}
