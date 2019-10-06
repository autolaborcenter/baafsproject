package com.marvelmind

import cn.autolabor.serialport.parser.SerialPortFinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.mechdancer.BuilderDslMarker
import org.mechdancer.WatchDog
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.common.Stamped
import org.mechdancer.exceptions.DataTimeoutException
import org.mechdancer.exceptions.DeviceNotExistException

@BuilderDslMarker
class MobileBeaconModuleBuilderDsl private constructor() {
    var port: String? = null
    var openTimeout: Long = 1000L
    var dataTimeout: Long = 2000L

    companion object {
        fun CoroutineScope.startMobileBeacon(
            beaconOnMap: SendChannel<Stamped<Vector2D>>,
            block: MobileBeaconModuleBuilderDsl.() -> Unit = {}
        ) {
            MobileBeaconModuleBuilderDsl()
                .apply(block)
                .run {
                    val watchDog = WatchDog(dataTimeout)
                    val resource = Resource(port, openTimeout) { time, x, y ->
                        launch { beaconOnMap.send(Stamped(time, vector2DOf(x, y))) }
                        launch { if (!watchDog.feed()) throw DataTimeoutException("marvelmind mobile beacon") }
                    }
                    launch { resource.use { while (isActive) it() } }
                        .invokeOnCompletion { beaconOnMap.close() }
                }
        }
    }

    // Marvelmind 移动标签资源控制器
    private class Resource(
        name: String?,
        timeout: Long,
        private val callback: (Long, Double, Double) -> Unit
    ) : cn.autolabor.Resource {
        private companion object {
            const val BUFFER_SIZE = 32
        }

        private val engine = engine()
        private val port =
            SerialPortFinder.findSerialPort(name, engine) {
                baudRate = 115200
                timeoutMs = timeout
                bufferSize = BUFFER_SIZE
                condition { (code, _) -> code == 0x11 }
            } ?: throw DeviceNotExistException("marvelmind mobile beacon")

        private val buffer = ByteArray(BUFFER_SIZE)
        override val info: String
            get() = port.descriptivePortName

        override operator fun invoke() {
            synchronized(port) {
                port.takeIf { it.isOpen }?.readBytes(buffer, buffer.size.toLong())
            }?.takeIf { it > 0 }
                ?.let { buffer.asList().subList(0, it) }
                ?.let { buffer ->
                    engine(buffer) { (code, payload) ->
                        val now = System.currentTimeMillis()
                        if (code != 0x11) return@engine
                        val value = ResolutionCoordinate(payload)
                        value
                            .delay
                            .takeIf { it in 1..399 }
                            ?.let { callback(now - it, value.x / 1000.0, value.y / 1000.0) }
                    }
                }
        }

        override fun close() {
            synchronized(port) { port.closePort() }
        }
    }
}
