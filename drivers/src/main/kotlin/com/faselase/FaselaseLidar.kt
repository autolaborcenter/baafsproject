package com.faselase

import cn.autolabor.serialport.SerialPortFinder
import cn.autolabor.serialport.manager.readOrReboot
import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.mechdancer.SimpleLogger
import org.mechdancer.WatchDog
import org.mechdancer.common.Polar
import org.mechdancer.common.Stamped
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.exceptions.ExceptionMessage.Occurred
import org.mechdancer.exceptions.ExceptionMessage.Recovered
import org.mechdancer.exceptions.device.DataTimeoutException
import org.mechdancer.exceptions.device.DeviceNotExistException
import org.mechdancer.exceptions.device.DeviceOfflineException
import java.util.concurrent.Executors
import kotlin.math.PI

/**
 * 砝石雷达资源控制
 */
internal class FaselaseLidar(
    scope: CoroutineScope,
    private val exceptions: SendChannel<ExceptionMessage>,

    portName: String?,
    tag: String?,

    launchTimeout: Long,
    connectionTimeout: Long,
    private val dataTimeout: Long,
    retryInterval: Long
) : CoroutineScope by scope {
    // 解析
    private val engine = engine()
    // 缓存
    private val queue = PolarFrameCollectorQueue()
    // 访问
    val tag: String
    val frame get() = queue.get()

    private val offlineException: DeviceOfflineException
    private val connectionWatchDog: WatchDog

    private val dataTimeoutException: DataTimeoutException
    private val dataWatchDog: WatchDog

    private val logger: SimpleLogger

    init {
        val port =
            try {
                val candidates =
                    portName?.let { listOf(SerialPort.getCommPort(it)) }
                    ?: SerialPort.getCommPorts()
                        ?.takeIf(Array<*>::isNotEmpty)
                        ?.toList()
                    ?: throw RuntimeException("no available port")
                SerialPortFinder
                    .findSerialPort(
                            candidates = candidates,
                            engine = engine
                    ) {
                        baudRate = 460800
                        timeoutMs = launchTimeout
                        bufferSize = BUFFER_SIZE
                        //  启动时发送开始旋转指令
                        activate = "#SF 10\r\n".toByteArray(Charsets.US_ASCII)
                        condition { pack -> pack is LidarPack.Data }
                    }
            } catch (e: RuntimeException) {
                throw DeviceNotExistException(tag ?: "faselase lidar", e.message)
            }

        this.tag = tag ?: port.descriptivePortName
        logger = SimpleLogger(this.tag).apply { period = 65536 }

        offlineException = DeviceOfflineException(this.tag)
        dataTimeoutException = DataTimeoutException(this.tag, dataTimeout)
        connectionWatchDog = WatchDog(this, connectionTimeout) { exceptions.send(Occurred(offlineException)) }
        dataWatchDog = WatchDog(this, dataTimeout) { exceptions.send(Occurred(dataTimeoutException)) }

        launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) {
            val buffer = ByteArray(BUFFER_SIZE)
            while (true)
                port.readOrReboot(buffer, retryInterval) {
                    exceptions.send(Occurred(DeviceOfflineException(this@FaselaseLidar.tag)))
                }.takeIf(Collection<*>::isNotEmpty)?.let(::write)
        }.invokeOnCompletion {
            port.closePort()
        }
    }

    private fun write(list: List<Byte>) {
        connectionWatchDog.feed()
        launch { exceptions.send(Recovered(offlineException)) }
        engine(list) { pack ->
            when (pack) {
                LidarPack.Nothing    -> logger.log("nothing")
                LidarPack.Failed     -> logger.log("failed")
                is LidarPack.Invalid -> {
                    val (theta) = pack
                    queue.refresh(theta.onPeriod())
                    logger.log(Double.NaN, theta)
                }
                is LidarPack.Data    -> {
                    dataWatchDog.feed()
                    launch { exceptions.send(Recovered(dataTimeoutException)) }
                    val (rho, theta) = pack
                    queue += Stamped.stamp(Polar(rho, theta.onPeriod()))
                    logger.log(rho, theta)
                }
            }
        }
    }

    private var offset = .0
    private var last = .0
    private fun Double.onPeriod(): Double {
        if (this < last) offset += 2 * PI
        last = this
        return this + offset
    }

    companion object {
        private const val BUFFER_SIZE = 64
    }
}
