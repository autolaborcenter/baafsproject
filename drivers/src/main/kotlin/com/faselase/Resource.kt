package com.faselase

import cn.autolabor.serialport.parser.SerialPortFinder
import org.mechdancer.common.Polar
import org.mechdancer.common.Stamped
import org.mechdancer.exceptions.DeviceNotExistException
import java.io.Closeable
import java.util.*
import kotlin.math.PI

/**
 * 砝石雷达资源控制器
 * 用户可自选调度器，反复调用 [invoke] 方法以运行
 */
class Resource(
    name: String? = null,
    private val callback: (List<Stamped<Polar>>) -> Unit
) : Closeable {
    private val engine = engine()
    private val port =
        //  启动时发送开始旋转指令
        try {
            SerialPortFinder.findSerialPort(name, engine) {
                baudRate = 460800
                timeoutMs = 5000
                bufferSize = 32
                activate = "#SF 10\r\n".toByteArray(Charsets.US_ASCII)
                condition { pack -> pack is LidarPack.Data }
            }
        } catch (e: RuntimeException) {
            throw DeviceNotExistException("faselase lidar", e.message)
        }
    private val buffer = ByteArray(256)
    private val list = LinkedList<Stamped<Polar>>()
    private var offset = .0
    private var last = .0

    val info: String get() = port.descriptivePortName

    operator fun invoke() {
        synchronized(port) {
            port.takeIf { it.isOpen }?.readBytes(buffer, buffer.size.toLong())
        }?.takeIf { it > 0 }
            ?.let { buffer.asList().subList(0, it) }
            ?.let { buffer ->
                engine(buffer) { pack ->
                    when (pack) {
                        is LidarPack.Failed  -> Unit
                        is LidarPack.Nothing -> refresh(pack.theta)
                        is LidarPack.Data    -> {
                            val (rho, theta) = pack
                            list.offer(Stamped.stamp(Polar(rho, refresh(theta))))
                        }
                    }
                }
            }
            ?.also { callback(list) }
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
}
