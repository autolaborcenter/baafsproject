package org.mechdancer.baafs.modules

import cn.autolabor.core.server.ServerManager
import cn.autolabor.message.navigation.Msg2DOdometry
import cn.autolabor.util.lambda.LambdaFunWithName
import cn.autolabor.util.lambda.function.TaskLambdaFun01
import cn.autolabor.util.reflect.TypeNode
import com.marvelmind.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.baafs.modules.LinkMode.Direct
import org.mechdancer.baafs.modules.LinkMode.Framework
import org.mechdancer.common.Stamped
import org.mechdancer.exceptions.DataTimeoutException
import java.util.concurrent.atomic.AtomicLong

/** 以 [mode] 模式启动底盘 */
fun CoroutineScope.startBeacon(
    mode: LinkMode,
    beaconOnMap: SendChannel<Stamped<Vector2D>>
) {
    when (mode) {
        Direct    -> {
            val i = AtomicLong(0)
            val resource = Resource { time, x, y ->
                launch { beaconOnMap.send(Stamped(time, vector2DOf(x, y))) }
                launch {
                    val mark = i.incrementAndGet()
                    delay(2000L)
                    if (i.get() == mark) throw DataTimeoutException("marvelmind mobile beacon")
                }
            }
            launch {
                resource.use { while (isActive) it() }
            }.invokeOnCompletion {
                beaconOnMap.close()
            }
        }
        Framework -> {
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
    }
}
