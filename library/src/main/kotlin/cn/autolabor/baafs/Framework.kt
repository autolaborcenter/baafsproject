package cn.autolabor.baafs

import cn.autolabor.core.server.ServerManager
import cn.autolabor.core.server.message.MessageHandle
import cn.autolabor.message.navigation.Msg2DOdometry
import cn.autolabor.util.lambda.LambdaFunWithName
import cn.autolabor.util.lambda.function.TaskLambdaFun01
import cn.autolabor.util.reflect.TypeNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped

inline fun <reified T> String.handler(): MessageHandle<Any> =
    ServerManager.me().getOrCreateMessageHandle(this, TypeNode(T::class.java))

internal fun CoroutineScope.startBeaconOnFramework(
    beaconOnMap: SendChannel<Stamped<Vector2D>>
) {
    (ServerManager.me().getConfig("MarvelmindTask", "topic") as? String ?: "abs")
        .handler<Msg2DOdometry>()
        .addCallback(LambdaFunWithName("locate_handle", object : TaskLambdaFun01<Msg2DOdometry> {
            override fun run(p0: Msg2DOdometry?) {
                launch {
                    p0?.run { beaconOnMap.send(Stamped(header.stamp, vector2DOf(pose.x, pose.y))) }
                }
            }
        }))
}

@ExperimentalCoroutinesApi
internal fun CoroutineScope.startChassisOnFramework(
    odometry: SendChannel<Stamped<Odometry>>
) {
    (ServerManager.me().getConfig("PM1Task", "odometryTopic") as? String ?: "odometry")
        .handler<Msg2DOdometry>()
        .addCallback(
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
