package com.marvelmind

import cn.autolabor.Resource
import cn.autolabor.serialport.parser.SerialPortFinder.Companion.findSerialPort
import org.mechdancer.exceptions.DeviceNotExistException

/**
 * Marvelmind 移动标签资源控制器
 * 用户可自选调度器，反复调用 [invoke] 方法以运行
 */
class Resource(
    name: String? = null,
    private val callback: (Long, Double, Double) -> Unit
) : Resource {
    private val engine = engine()
    private val port =
        findSerialPort(name, engine) {
            baudRate = 115200
            timeoutMs = 1000
            bufferSize = 32
            condition { (code, _) -> code == 0x11 }
        } ?: throw DeviceNotExistException("marvelmind mobile beacon")

    private val buffer = ByteArray(32)
    override val info: String
        get() = port.descriptivePortName

    override operator fun invoke() {
        synchronized(this) {
            port.takeIf { it.isOpen }?.readBytes(buffer, buffer.size.toLong())
        }?.takeIf { it > 0 }
            ?.let { buffer.asList().subList(0, it) }
            ?.let { buffer ->
                engine(buffer) { (code, payload) ->
                    val now = System.currentTimeMillis()
                    if (code != 0x11) return@engine
                    val value = ResolutionCoordinate(payload)
                    value
                        .delay
                        .takeIf { it in 1..399 }
                        ?.let { callback(now - it, value.x / 1000.0, value.y / 1000.0) }
                }
            }
    }

    override fun close() {
        synchronized(this) { port.closePort() }
    }
}
