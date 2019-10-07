package com.marvelmind

import cn.autolabor.serialport.parser.SerialPortFinder
import com.fazecast.jSerialComm.SerialPort
import com.marvelmind.BeaconPackage.*
import com.marvelmind.BeaconPackage.Nothing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.mechdancer.BuilderDslMarker
import org.mechdancer.SimpleLogger
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
        private const val NAME = "marvelmind mobile beacon"
        private const val BUFFER_SIZE = 32
        private const val COORDINATE_CODE = 0x111

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
                        launch { if (!watchDog.feed()) throw DataTimeoutException(NAME) }
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
        override val info: String
            get() = port.descriptivePortName

        private val logger = SimpleLogger(NAME)
        private val buffer = ByteArray(BUFFER_SIZE)
        private val engine = engine()
        private val port =
            SerialPortFinder.findSerialPort(name, engine) {
                baudRate = 115200
                timeoutMs = timeout
                bufferSize = BUFFER_SIZE
                condition { it is Data && it.code == COORDINATE_CODE }
            } ?: throw DeviceNotExistException(NAME)

        override operator fun invoke() {
            synchronized(port) {
                port.takeIf(SerialPort::isOpen)?.readBytes(buffer, buffer.size.toLong())
            }?.takeIf { it > 0 }
                ?.let(buffer::take)
                ?.let { array ->
                    engine(array) { pack ->
                        when (pack) {
                            Nothing -> Unit
                            Failed  -> logger.log("failed")
                            is Data -> {
                                val (code, payload) = pack
                                if (code != COORDINATE_CODE)
                                    logger.log("code = $code")
                                else {
                                    val now = System.currentTimeMillis()
                                    val value = ResolutionCoordinate(payload)
                                    val x = value.x / 1000.0
                                    val y = value.y / 1000.0
                                    val delay = value.delay
                                    logger.log("delay = $delay, x = $x, y = $y")
                                    delay.takeIf { it in 1..399 }?.let { callback(now - it, x, y) }
                                }
                            }
                        }
                    }
                }
        }

        override fun close() {
            synchronized(port) { port.closePort() }
        }
    }
}
