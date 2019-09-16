package org.mechdancer.modules.devices

import cn.autolabor.Stamped
import cn.autolabor.core.server.ServerManager
import cn.autolabor.message.navigation.Msg2DOdometry
import cn.autolabor.util.reflect.TypeNode
import com.marvelmind.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf

/**
 * 定位器
 */
sealed class Locator {
    protected val locateChannel = Channel<Stamped<Vector2D>>(Channel.CONFLATED)
    val robotLocation: ReceiveChannel<Stamped<Vector2D>> get() = locateChannel

    /**
     * 框架定位器
     */
    class FrameworkRemoteLocator(scope: CoroutineScope) : Locator() {
        init {
            val topic = ServerManager.me().getConfig("MarvelmindTask", "topic") as? String ?: "abs"
            val locate = ServerManager.me().getOrCreateMessageHandle(topic, TypeNode(Msg2DOdometry::class.java))
            scope.launch {
                while (isActive) {
                    (locate.firstData as? Msg2DOdometry)?.run {
                        locateChannel.send(Stamped(header.stamp, vector2DOf(pose.x, pose.y)))
                    }
                    delay(100L)
                }
                println("over")
            }
        }
    }

    /**
     * marvelmind 定位器
     */
    class MarvelmindLocator(scope: CoroutineScope) : Locator() {
        init {
            scope.launch {
                Resource { time, x, y ->
                    scope.launch { locateChannel.send(Stamped(time, vector2DOf(x, y))) }
                }.use { while (isActive) it() }
            }
        }
    }
}
