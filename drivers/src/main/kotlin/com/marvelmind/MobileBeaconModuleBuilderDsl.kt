package com.marvelmind

import cn.autolabor.serialport.parser.SerialPortFinder
import com.marvelmind.BeaconPackage.*
import com.marvelmind.BeaconPackage.Nothing
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import org.mechdancer.BuilderDslMarker
import org.mechdancer.SimpleLogger
import org.mechdancer.WatchDog
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.common.Stamped
import org.mechdancer.exceptions.DeviceNotExistException
import org.mechdancer.exceptions.DeviceOfflineException
import java.util.concurrent.Executors

@BuilderDslMarker
class MobileBeaconModuleBuilderDsl private constructor() {
    // 指定串口名字
    var port: String? = null
    // 数据接收参数
    var retryInterval: Long = 100L
    var retryTimes: Int = 3
    var connectionTimeout: Long = 2000L
    var dataTimeout: Long = 2000L
    var delayLimit: Long = 400L

    companion object {
        fun CoroutineScope.startMobileBeacon(
            beaconOnMap: SendChannel<Stamped<Vector2D>>,
            block: MobileBeaconModuleBuilderDsl.() -> Unit = {}
        ) {
            MobileBeaconModuleBuilderDsl()
                .apply(block)
                .apply {
                    require(connectionTimeout > 0)
                    require(retryTimes > 0)
                    require(retryInterval > 0)
                    require(dataTimeout > retryTimes * retryInterval)
                    require(delayLimit > 1)
                }
                .run {
                    MarvelmindMobilBeacon(
                        scope = this@startMobileBeacon,
                        beaconOnMap = beaconOnMap,
                        name = port,
                        connectionTimeout = connectionTimeout,
                        dataTimeout = dataTimeout,
                        retryInterval = retryInterval,
                        retryTimes = retryTimes,
                        delayLimit = delayLimit)
                }
        }
    }

    // Marvelmind 移动标签资源控制器
    private class MarvelmindMobilBeacon(
        private val scope: CoroutineScope,
        private val beaconOnMap: SendChannel<Stamped<Vector2D>>,
        name: String?,
        dataTimeout: Long,
        connectionTimeout: Long,
        private val retryInterval: Long,
        private val retryTimes: Int,
        private val delayLimit: Long
    ) {
        private val logger = SimpleLogger(NAME)
        private val buffer = ByteArray(BUFFER_SIZE)
        private val engine = engine()
        private val port =
            SerialPortFinder.findSerialPort(name, engine) {
                baudRate = 115200
                timeoutMs = dataTimeout
                bufferSize = BUFFER_SIZE
                condition { it is Data && it.code == COORDINATE_CODE }
            } ?: throw DeviceNotExistException(NAME)

        // 单开线程以执行阻塞读取
        private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        // 连接监视
        private val connectionWatchDog = WatchDog(connectionTimeout)
        // 数据监视
        private val locationWatchDog = WatchDog(dataTimeout)

        init {
            scope.launch {
                while (true) read().takeIf(Collection<*>::isNotEmpty)?.let { write(it) }
            }.invokeOnCompletion {
                runBlocking(dispatcher) { port.closePort() }
                beaconOnMap.close()
            }
        }

        private suspend fun read(): List<Byte> {
            // 反复尝试读取
            for (i in 1..retryTimes) {
                // 在单线程上操作串口
                withContext(dispatcher) {
                    // 如果开着或者能开才尝试读取
                    if (port.isOpen || port.openPort())
                        when (val size = port.readBytes(buffer, buffer.size.toLong())) {
                            -1   -> null
                            0    -> {
                                // 如果长度是 0,的可能是假的,发送空包可更新串口对象状态
                                port.writeBytes(byteArrayOf(), 0)
                                if (port.isOpen) emptyList() else null
                            }
                            else -> buffer.take(size)
                        }
                    else null
                }?.also { return it }
                // 等待一段时间重试
                delay(retryInterval)
            }
            logger.log("port closed, and failed to try for $retryTimes times")
            throw DeviceOfflineException(NAME)
        }

        private fun write(array: List<Byte>) {
            engine(array) { pack ->
                scope.launch {
                    if (!connectionWatchDog.feed()) {
                        logger.log("data timeout, close port to reboot")
                        withContext(dispatcher) { port.closePort() }
                    }
                }
                when (pack) {
                    is Nothing -> logger.log("nothing${pack.dropped.toHexString()}")
                    is Failed  -> logger.log("failed${pack.dropped.toHexString()}")
                    is Data    -> {
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
                            delay.takeIf { it in 1 until delayLimit }
                                ?.let {
                                    scope.launch { beaconOnMap.send(Stamped(now - it, vector2DOf(x, y))) }
                                    scope.launch {
                                        if (!locationWatchDog.feed()) {
                                            logger.log("data timeout, close port to reboot")
                                            withContext(dispatcher) { port.closePort() }
                                        }
                                    }
                                }
                        }
                    }
                }
            }
        }

        private companion object {
            private const val NAME = "marvelmind mobile beacon"
            private const val BUFFER_SIZE = 32
            private const val COORDINATE_CODE = 0x11

            fun ByteArray.toHexString() =
                "[${joinToString(" ") { Integer.toHexString(it.toInt()) }}]"
        }
    }
}
