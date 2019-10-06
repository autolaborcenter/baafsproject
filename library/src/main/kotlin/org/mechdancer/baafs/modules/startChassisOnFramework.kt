package org.mechdancer.baafs.modules

import cn.autolabor.core.server.ServerManager
import cn.autolabor.message.navigation.Msg2DOdometry
import cn.autolabor.util.lambda.LambdaFunWithName
import cn.autolabor.util.lambda.function.TaskLambdaFun01
import cn.autolabor.util.reflect.TypeNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped

@ExperimentalCoroutinesApi
fun CoroutineScope.startChassisOnFramework(
    odometry: SendChannel<Stamped<Odometry>>
) {
    with(ServerManager.me()) {
        getOrCreateMessageHandle(
            getConfig("PM1Task", "odometryTopic") as? String ?: "odometry",
            TypeNode(Msg2DOdometry::class.java)
        ).addCallback(
            LambdaFunWithName(
                "odometry_handel",
                object : TaskLambdaFun01<Msg2DOdometry> {
                    override fun run(p0: Msg2DOdometry?) {
                        if (odometry.isClosedForSend) return
                        val data = p0?.pose ?: return
                        launch {
                            odometry.send(
                                Stamped(
                                    p0.header.stamp,
                                    Odometry.odometry(data.x, data.y, data.yaw)
                                )
                            )
                        }
                    }
                })
        )
    }
}
