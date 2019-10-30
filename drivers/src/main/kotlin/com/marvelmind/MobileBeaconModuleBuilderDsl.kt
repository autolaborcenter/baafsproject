package com.marvelmind

import cn.autolabor.serialport.parser.SerialPortFinder
import cn.autolabor.serialport.parser.readOrReboot
import com.fazecast.jSerialComm.SerialPort
import com.marvelmind.BeaconPackage.*
import com.marvelmind.BeaconPackage.Nothing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.mechdancer.BuilderDslMarker
import org.mechdancer.SimpleLogger
import org.mechdancer.WatchDog
import org.mechdancer.algebra.function.vector.div
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.common.Stamped
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.exceptions.ExceptionMessage.Occurred
import org.mechdancer.exceptions.device.DataTimeoutException
import org.mechdancer.exceptions.device.DeviceNotExistException
import org.mechdancer.exceptions.device.DeviceOfflineException
import org.mechdancer.exceptions.device.ParseTimeoutException
import java.util.concurrent.Executors

@BuilderDslMarker
class MobileBeaconModuleBuilderDsl private constructor() {
    // 指定串口名字
    var port: String? = null
    // 数据接收参数
    var retryInterval: Long = 100L
    var connectionTimeout: Long = 2000L
    var parseTimeout: Long = 2000L
    var dataTimeout: Long = 2000L
    var delayLimit: Long = 400L

    companion object {
        fun CoroutineScope.startMobileBeacon(
            beaconOnMap: SendChannel<Stamped<Vector2D>>,
            exceptions: SendChannel<ExceptionMessage>,
            block: MobileBeaconModuleBuilderDsl.() -> Unit = {}
        ) {
            MobileBeaconModuleBuilderDsl()
                .apply(block)
                .apply {
                    require(retryInterval > 0)
                    require(connectionTimeout > 0)
                    require(parseTimeout > 0)
                    require(dataTimeout > 0)
                    require(delayLimit > 1)
                }
                .run {
                    MarvelmindMobilBeacon(
                        scope = this@startMobileBeacon,
                        beaconOnMap = beaconOnMap,
                        exceptions = exceptions,
                        name = port,
                        connectionTimeout = connectionTimeout,
                        parseTimeout = parseTimeout,
                        dataTimeout = dataTimeout,
                        retryInterval = retryInterval,
                        delayLimit = delayLimit
                    )
                }
        }
    }

    // Marvelmind 移动标签资源控制器
    private class MarvelmindMobilBeacon(
        scope: CoroutineScope,
        private val beaconOnMap: SendChannel<Stamped<Vector2D>>,
        private val exceptions: SendChannel<ExceptionMessage>,
        name: String?,
        connectionTimeout: Long,
        private val parseTimeout: Long,
        private val dataTimeout: Long,
        private val retryInterval: Long,
        private val delayLimit: Long
    ) : CoroutineScope by scope {
        private val logger = SimpleLogger(NAME)
        private val buffer = ByteArray(BUFFER_SIZE)
        private val engine = engine()
        private val port =
            try {
                val candidates =
                    name?.let { listOf(SerialPort.getCommPort(it)) }
                        ?: SerialPort.getCommPorts()
                            ?.takeIf(Array<*>::isNotEmpty)
                            ?.groupBy { "marvelmind" in it.systemPortName.toLowerCase() }
                            ?.flatMap { it.value }
                            ?.toList()
                        ?: throw RuntimeException("no available port")
                SerialPortFinder.findSerialPort(
                    candidates = candidates,
                    engine = engine
                ) {
                    baudRate = 115200
                    timeoutMs = dataTimeout
                    bufferSize = BUFFER_SIZE
                    condition { it is Data && it.code == COORDINATE_CODE }
                }
            } catch (e: RuntimeException) {
                throw DeviceNotExistException(NAME, e.message)
            }

        // 超时异常监控
        private val connectionWatchDog = WatchDog(connectionTimeout)
        private val parseWatchDog = WatchDog(parseTimeout)
        private val dataWatchDog = WatchDog(dataTimeout)

        init {
            // 单开线程以执行阻塞读取
            launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) {
                while (true)
                    port.readOrReboot(buffer, retryInterval) {
                        exceptions.send(Occurred(DeviceOfflineException(NAME)))
                    }.let { if (it.isNotEmpty()) write(it) }
            }.invokeOnCompletion {
                port.closePort()
                beaconOnMap.close()
            }
        }

        private var memory = Triple(0, 0, 0)
        private fun write(array: List<Byte>) {
            launch {
                connectionWatchDog.feedOrThrowTo(
                    exceptions,
                    DeviceOfflineException(NAME)
                )
            }
            engine(array) { pack ->
                when (pack) {
                    is Nothing -> logger.log("nothing${pack.dropped.toHexString()}")
                    is Failed -> logger.log("failed${pack.dropped.toHexString()}")
                    is Data -> {
                        launch {
                            parseWatchDog.feedOrThrowTo(
                                exceptions,
                                ParseTimeoutException(NAME, parseTimeout)
                            )
                        }
                        val (code, payload) = pack
                        if (code != COORDINATE_CODE)
                            logger.log("code = $code")
                        else {
                            val now = System.currentTimeMillis()
                            val value = ResolutionCoordinate(payload)
                            val x = value.x
                            val y = value.y
                            val z = value.z
                            val delay = value.delay
                            logger.log("delay = $delay, x = ${x / 1000.0}, y = ${y / 1000.0}")

                            if (delay !in 1 until delayLimit) return@engine

                            val last = memory
                            memory = Triple(x, y, z)
                            if (memory == last) return@engine

                            launch {
                                beaconOnMap.send(Stamped(now - delay, vector2DOf(x, y) / 1000.0))
                                dataWatchDog.feedOrThrowTo(
                                    exceptions,
                                    DataTimeoutException(NAME, dataTimeout)
                                )
                            }
                        }
                    }
                }
            }
        }

        private companion object {
            const val NAME = "marvelmind mobile beacon"
            const val BUFFER_SIZE = 32
            const val COORDINATE_CODE = 0x11

            fun ByteArray.toHexString() =
                "[${joinToString(" ") { Integer.toHexString(it.toInt()).takeLast(2) }}]"
        }
    }
}
