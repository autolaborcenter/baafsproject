package com.faselase

import cn.autolabor.serialport.parser.SerialPortFinder
import cn.autolabor.serialport.parser.readOrReboot
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
import org.mechdancer.exceptions.device.DataTimeoutException
import org.mechdancer.exceptions.device.DeviceNotExistException
import org.mechdancer.exceptions.device.DeviceOfflineException
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.PI

class FaselaseLidar(
    scope: CoroutineScope,
    exceptions: SendChannel<ExceptionMessage>,
    name: String?,
    tag: String?,
    launchTimeout: Long,
    connectionTimeout: Long,
    private val dataTimeout: Long,
    retryInterval: Long
) : CoroutineScope by scope {
    // 解析
    private val engine = engine()
    private val port =
        try {
            val candidates =
                name?.let { listOf(SerialPort.getCommPort(it)) }
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
    // 接收
    private val buffer = ByteArray(BUFFER_SIZE)
    private val connectionWatchDog = WatchDog(connectionTimeout)
    private val dataWatchDog = WatchDog(dataTimeout)
    // 缓存
    private val list = LinkedList<Stamped<Polar>>()
    private val lock = ReentrantReadWriteLock()
    private var offset = .0
    private var last = .0
    // 访问
    val tag = tag ?: port.descriptivePortName
    val frame get() = lock.read { list.toList() }
    // 日志
    private val logger = SimpleLogger(this.tag).apply { period = 4096 }

    init {
        launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) {
            val deviceTag = this@FaselaseLidar.tag
            while (port.isOpen)
                port.readOrReboot(buffer, retryInterval) {
                    exceptions.send(Occurred(DeviceOfflineException(deviceTag)))
                }.takeIf(Collection<*>::isNotEmpty)
                    ?.let { buffer ->
                        launch {
                            connectionWatchDog.feedOrThrowTo(
                                exceptions,
                                DeviceOfflineException(deviceTag)
                            )
                        }
                        engine(buffer) { pack ->
                            when (pack) {
                                LidarPack.Nothing -> logger.log("nothing")
                                LidarPack.Failed -> logger.log("failed")
                                is LidarPack.Invalid -> {
                                    val (theta) = pack
                                    lock.write { refresh(theta) }
                                    logger.log(Double.NaN, theta)
                                }
                                is LidarPack.Data -> {
                                    launch {
                                        dataWatchDog.feedOrThrowTo(
                                            exceptions,
                                            DataTimeoutException(deviceTag, dataTimeout)
                                        )
                                    }
                                    val (rho, theta) = pack
                                    lock.write { list.offer(Stamped.stamp(Polar(rho, refresh(theta)))) }
                                    logger.log(rho, theta)
                                }
                            }
                        }
                    }
        }.invokeOnCompletion {
            port.closePort()
        }
    }

    private fun refresh(theta: Double): Double {
        if (theta < last) offset += 2 * PI
        last = theta
        val t = theta + offset
        val head = t - 2 * PI
        while (list.firstOrNull()?.data?.angle?.let { it < head } == true) list.poll()
        return t
    }

    companion object {
        private const val BUFFER_SIZE = 64
    }
}
