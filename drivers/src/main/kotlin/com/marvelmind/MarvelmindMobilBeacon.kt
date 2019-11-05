package com.marvelmind

import cn.autolabor.serialport.parser.SerialPortFinder
import cn.autolabor.serialport.parser.readOrReboot
import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.mechdancer.SimpleLogger
import org.mechdancer.WatchDog
import org.mechdancer.algebra.function.vector.div
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.common.Stamped
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.exceptions.ExceptionMessage.Occurred
import org.mechdancer.exceptions.ExceptionMessage.Recovered
import org.mechdancer.exceptions.device.DataTimeoutException
import org.mechdancer.exceptions.device.DeviceNotExistException
import org.mechdancer.exceptions.device.DeviceOfflineException
import org.mechdancer.exceptions.device.ParseTimeoutException
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Marvelmind 移动标签资源控制
 */
internal class MarvelmindMobilBeacon(
    scope: CoroutineScope,
    private val beaconOnMap: SendChannel<Stamped<Vector2D>>,
    private val exceptions: SendChannel<ExceptionMessage>,

    portName: String?,

    connectionTimeout: Long,
    private val parseTimeout: Long,
    private val dataTimeout: Long,
    private val retryInterval: Long,

    delayLimit: Long,
    heightRange: ClosedFloatingPointRange<Double>,

    private val logger: SimpleLogger?
) : CoroutineScope by scope {
    // 协议解析引擎
    private val engine = engine()
    // 超时异常监控
    private val offlineException = DeviceOfflineException(NAME)
    private val connectionWatchDog = WatchDog(this, connectionTimeout) { exceptions.send(Occurred(offlineException)) }

    private val parseTimeoutException = ParseTimeoutException(NAME, parseTimeout)
    private val parseWatchDog = WatchDog(this, parseTimeout) { exceptions.send(Occurred(parseTimeoutException)) }

    private val dataTimeoutException = DataTimeoutException(NAME, dataTimeout)
    private val dataWatchDog = WatchDog(this, dataTimeout) { exceptions.send(Occurred(dataTimeoutException)) }
    // 数据过滤
    private val delayRange = 1..delayLimit
    private val zRange = heightRange.start.roundToInt()..heightRange.endInclusive.roundToInt()

    // 常量参数
    private companion object {
        const val NAME = "marvelmind mobile beacon"
        const val BUFFER_SIZE = 32
        const val COORDINATE_CODE = 0x11
    }

    init {
        // 打开串口
        val port =
            try {
                // 若不指定，优先选取名字里带 "marvelmind" 的串口
                val candidates =
                    portName
                        ?.let { listOf(SerialPort.getCommPort(it)) }
                    ?: SerialPort.getCommPorts()
                        ?.takeIf(Array<*>::isNotEmpty)
                        ?.groupBy { "marvelmind" in it.systemPortName.toLowerCase() }
                        ?.flatMap { it.value }
                        ?.toList()
                    ?: throw RuntimeException("no available port")
                // 打开串口
                SerialPortFinder.findSerialPort(
                        candidates = candidates,
                        engine = engine
                ) {
                    baudRate = 115200
                    timeoutMs = dataTimeout
                    bufferSize = BUFFER_SIZE
                    condition { it is BeaconPackage.Data && it.code == COORDINATE_CODE }
                }
            } catch (e: RuntimeException) {
                throw DeviceNotExistException(NAME, e.message)
            }
        // 单开线程以执行阻塞读取
        launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) {
            val buffer = ByteArray(BUFFER_SIZE)
            while (true)
                port.readOrReboot(buffer, retryInterval) {
                    exceptions.send(Occurred(DeviceOfflineException(NAME)))
                }.takeUnless(List<*>::isEmpty)?.let(::write)
        }.invokeOnCompletion {
            port.closePort()
            beaconOnMap.close()
        }
    }

    private var memory = Triple(0, 0, 0)
    private fun notStatic(x: Int, y: Int, z: Int): Boolean {
        val last = memory
        memory = Triple(x, y, z)
        return memory != last
    }

    private fun write(array: List<Byte>) {
        connectionWatchDog.feed()
        launch { exceptions.send(Recovered(offlineException)) }
        engine(array) { pack ->
            when (pack) {
                is BeaconPackage.Nothing -> logger?.log("nothing")
                is BeaconPackage.Failed  -> logger?.log("failed")
                is BeaconPackage.Data    -> {
                    parseWatchDog.feed()
                    launch { exceptions.send(Recovered(parseTimeoutException)) }
                    val (code, payload) = pack
                    if (code != COORDINATE_CODE)
                        logger?.log("code = $code")
                    else {
                        val now = System.currentTimeMillis()
                        val value = ResolutionCoordinate(payload)
                        val x = value.x
                        val y = value.y
                        val z = value.z
                        val delay = value.delay
                        logger?.log("delay = $delay, x = ${x / 1000.0}, y = ${y / 1000.0}, z = ${z / 1000.0}")
                        // 过滤
                        if (delay in delayRange
                            && z in zRange
                            && notStatic(x, y, z)
                        ) launch {
                            beaconOnMap.send(Stamped(
                                    now - delay,
                                    vector2DOf(x, y) / 1000.0))
                            dataWatchDog.feed()
                            launch { exceptions.send(Recovered(dataTimeoutException)) }
                        }
                    }
                }
            }
        }
    }
}
