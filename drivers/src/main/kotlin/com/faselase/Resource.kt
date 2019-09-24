package com.faselase

import cn.autolabor.Resource
import cn.autolabor.serialport.parser.SerialPortFinder
import org.mechdancer.common.Polar
import org.mechdancer.common.Stamped
import java.util.*
import kotlin.math.PI

/**
 * 砝石雷达资源控制器
 * 用户可自选调度器，反复调用 [invoke] 方法以运行
 */
class Resource(
    private val callback: (List<Stamped<Polar>>) -> Unit
) : Resource {

    private val engine = engine(filter = true)
    private val port =
        //  启动时发送开始旋转指令
        SerialPortFinder.findSerialPort(engine) {
            baudRate = 460800
            timeoutMs = 5000
            bufferSize = 32
            activate = "#SF 10\r\n".toByteArray(Charsets.US_ASCII)
            condition { (rho, _) -> rho > 0 }
        } ?: throw RuntimeException("cannot find faselase lidar")
    private val buffer = ByteArray(256)
    private val list = LinkedList<Stamped<Polar>>()
    private var offset = .0
    private var last = .0

    override val info: String get() = port.descriptivePortName

    override operator fun invoke() {
        synchronized(port) {
            port.takeIf { it.isOpen }?.readBytes(buffer, buffer.size.toLong())
        }?.takeIf { it > 0 }
            ?.let { buffer.asList().subList(0, it) }
            ?.let { buffer ->
                engine(buffer) { (rho, theta) ->
                    if (rho <= 0) return@engine

                    if (theta < last) offset += 2 * PI
                    last = theta

                    list.offer(Stamped.stamp(Polar(rho, theta + offset)))

                    val head = theta + offset - 2 * PI
                    while (list.first().data.angle < head) list.poll()
                }
            }
            ?.also { callback(list) }
    }

    override fun close() {
        synchronized(port) { port.closePort() }
    }
}
