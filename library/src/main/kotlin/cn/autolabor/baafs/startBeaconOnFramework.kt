package cn.autolabor.baafs

import cn.autolabor.core.server.ServerManager
import cn.autolabor.message.navigation.Msg2DOdometry
import cn.autolabor.util.lambda.LambdaFunWithName
import cn.autolabor.util.lambda.function.TaskLambdaFun01
import cn.autolabor.util.reflect.TypeNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.common.Stamped

fun CoroutineScope.startBeaconOnFramework(beaconOnMap: SendChannel<Stamped<Vector2D>>) {
    with(ServerManager.me()) {
        getOrCreateMessageHandle(
            getConfig("MarvelmindTask", "topic") as? String ?: "abs",
            TypeNode(Msg2DOdometry::class.java)
        ).addCallback(LambdaFunWithName("locate_handle", object : TaskLambdaFun01<Msg2DOdometry> {
            override fun run(p0: Msg2DOdometry?) {
                launch {
                    p0?.run { beaconOnMap.send(Stamped(header.stamp, vector2DOf(pose.x, pose.y))) }
                }
            }
        }))
    }
}
