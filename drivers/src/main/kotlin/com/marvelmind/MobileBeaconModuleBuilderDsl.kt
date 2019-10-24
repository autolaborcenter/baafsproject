package com.marvelmind

import cn.autolabor.serialport.parser.SerialPortFinder
import com.marvelmind.BeaconPackage.*
import com.marvelmind.BeaconPackage.Nothing
import com.marvelmind.MobileBeaconException.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import org.mechdancer.BuilderDslMarker
import org.mechdancer.SimpleLogger
import org.mechdancer.WatchDog
import org.mechdancer.algebra.function.vector.div
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.common.Stamped
import org.mechdancer.exceptions.DeviceNotExistException
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.exceptions.ExceptionMessage.Occurred
import org.mechdancer.exceptions.ExceptionMessage.Recovered
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
    var maxSpeed: Double = 0.2

    companion object {
        fun CoroutineScope.startMobileBeacon(
            beaconOnMap: SendChannel<Stamped<Vector2D>>,
            exceptions: SendChannel<ExceptionMessage<MobileBeaconException>>,
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
                    require(maxSpeed > 0)
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
                        delayLimit = delayLimit,
                        maxSpeed = maxSpeed)
                }
        }
    }

    // Marvelmind 移动标签资源控制器
    private class MarvelmindMobilBeacon(
        private val scope: CoroutineScope,
        private val beaconOnMap: SendChannel<Stamped<Vector2D>>,
        private val exceptions: SendChannel<ExceptionMessage<MobileBeaconException>>,
        name: String?,
        connectionTimeout: Long,
        parseTimeout: Long,
        dataTimeout: Long,
        private val retryInterval: Long,
        private val delayLimit: Long,
        private val maxSpeed: Double
    ) {
        private val logger = SimpleLogger(NAME)
        private val buffer = ByteArray(BUFFER_SIZE)
        private val engine = engine()
        private val port =
            try {
                SerialPortFinder.findSerialPort(name, engine) {
                    baudRate = 115200
                    timeoutMs = dataTimeout
                    bufferSize = BUFFER_SIZE
                    condition { it is Data && it.code == COORDINATE_CODE }
                }
            } catch (e: RuntimeException) {
                throw DeviceNotExistException(NAME, e.message)
            }

        // 单开线程以执行阻塞读取
        private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        // 超时异常监控
        private val connectionWatchDog = WatchDog(connectionTimeout)
        private val parseWatchDog = WatchDog(parseTimeout)
        private val dataWatchDog = WatchDog(dataTimeout)
        // 状态

        init {
            scope.launch {
                while (true) read().takeIf(Collection<*>::isNotEmpty)?.let { write(it) }
            }.invokeOnCompletion {
                runBlocking(dispatcher) { port.closePort() }
                beaconOnMap.close()
            }
        }

        private fun WatchDog.feedOrThrow(exception: MobileBeaconException) {
            scope.launch {
                exceptions.send(Recovered(exception))
                if (!feed()) exceptions.send(Occurred(exception))
            }
        }

        private suspend fun read(): List<Byte> {
            // 反复尝试读取
            while (true) {
                // 在单线程上打开串口并阻塞读取
                val size = withContext(dispatcher) {
                    port.takeIf { it.isOpen || it.openPort() }
                        ?.readBytes(buffer, buffer.size.toLong())
                }
                when (size) {
                    null, -1 ->
                        exceptions.send(Occurred(DisconnectedException))
                    0        -> {
                        // 如果长度是 0,的可能是假的,发送空包可更新串口对象状态
                        port.writeBytes(byteArrayOf(), 0)
                        if (!port.isOpen) exceptions.send(Occurred(DisconnectedException))
                    }
                    else     -> {
                        connectionWatchDog.feedOrThrow(DisconnectedException)
                        return buffer.take(size)
                    }
                }
                // 等待一段时间重试
                delay(retryInterval)
            }
        }

        private var memory = Triple(0, 0, 0)
        private fun write(array: List<Byte>) {
            engine(array) { pack ->
                when (pack) {
                    is Nothing -> logger.log("nothing${pack.dropped.toHexString()}")
                    is Failed  -> logger.log("failed${pack.dropped.toHexString()}")
                    is Data    -> {
                        parseWatchDog.feedOrThrow(ParseTimeoutException)
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

                            scope.launch {
                                beaconOnMap.send(Stamped(now - delay, vector2DOf(x, y) / 1000.0))
                                dataWatchDog.feedOrThrow(DataTimeoutException)
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
