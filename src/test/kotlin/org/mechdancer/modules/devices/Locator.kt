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
import kotlinx.coroutines.launch
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import java.io.Closeable

sealed class Locator : Closeable {
    protected val locateChannel = Channel<Stamped<Vector2D>>(Channel.CONFLATED)
    protected var running = true

    val robotLocation: ReceiveChannel<Stamped<Vector2D>> get() = locateChannel

    override fun close() {
        running = false
    }

    class FrameworkRemoteLocator(scope: CoroutineScope) : Locator() {
        init {
            val locate = ServerManager.me().getOrCreateMessageHandle("abs", TypeNode(Msg2DOdometry::class.java))
            scope.launch {
                while (running) {
                    val temp = locate.firstData as? Msg2DOdometry ?: continue
                    val data = temp.pose
                    locateChannel.send(Stamped(temp.header.stamp, vector2DOf(data.x, data.y)))
                    delay(100L)
                }
                Resource { time, x, y ->
                    scope.launch { locateChannel.send(Stamped(time, vector2DOf(x, y))) }
                }.use { while (running) it() }
            }
        }
    }

    class MarvelmindLocator(scope: CoroutineScope) : Locator() {
        init {
            scope.launch {
                Resource { time, x, y ->
                    scope.launch { locateChannel.send(Stamped(time, vector2DOf(x, y))) }
                }.use { while (running) it() }
            }
        }
    }
}
