package com.faselase

import cn.autolabor.serialport.parser.SerialPortFinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mechdancer.DebugTemporary
import org.mechdancer.DebugTemporary.Operation.DELETE
import org.mechdancer.SimpleLogger
import org.mechdancer.common.Polar
import org.mechdancer.common.Stamped
import org.mechdancer.exceptions.DeviceNotExistException
import java.io.Closeable
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.PI

class FaselaseLidar(
    scope: CoroutineScope,
    portName: String?,
    tag: String?,
    connectionTimeout: Long
) : Closeable {
    // 解析
    private val engine = engine()
    private val port =
        try {
            SerialPortFinder.findSerialPort(portName, engine) {
                baudRate = 460800
                timeoutMs = connectionTimeout
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
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    // 缓存
    private val list = LinkedList<Stamped<Polar>>()
    private val lock = ReentrantReadWriteLock()
    private var offset = .0
    private var last = .0
    // 日志
    private val logger = SimpleLogger(tag ?: port.descriptivePortName)
    // 访问
    val frame get() = lock.read { list.toList() }

    @DebugTemporary(DELETE)
    val callbacks = mutableListOf<(Stamped<Polar>) -> Unit>()

    init {
        scope.launch {
            while (port.isOpen)
                withContext(dispatcher) { port.readBytes(buffer, buffer.size.toLong()) }
                    .takeIf { it > 0 }
                    ?.let { buffer.asList().subList(0, it) }
                    ?.let { buffer ->
                        engine(buffer) { pack ->
                            when (pack) {
                                LidarPack.Failed,
                                LidarPack.Nothing    -> Unit
                                is LidarPack.Invalid -> {
                                    val (theta) = pack
                                    lock.write { refresh(theta) }
                                    logger.log(Double.NaN, theta)
                                }
                                is LidarPack.Data    -> {
                                    val (rho, theta) = pack
                                    val data = Stamped.stamp(Polar(rho, refresh(theta)))
                                    lock.write { list.offer(data) }
                                    logger.log(rho, theta)
                                    synchronized(callbacks) {
                                        for (callback in callbacks) callback(data)
                                    }
                                }
                            }
                        }
                    }
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

    override fun close() {
        synchronized(port) { port.closePort() }
    }

    companion object {
        private const val BUFFER_SIZE = 32
    }
}
