package com.marvelmind

import cn.autolabor.Resource
import cn.autolabor.serialport.parser.SerialPortFinder

/**
 * Marvelmind 移动标签资源控制器
 * 用户可自选调度器，反复调用 [invoke] 方法以运行
 */
class Resource(
    private val callback: (Long, Double, Double) -> Unit
) : Resource {

    private val engine = engine()
    private val port =
        SerialPortFinder.findSerialPort(engine) {
            baudRate = 115200
            timeoutMs = 1000
            bufferSize = 32
            condition { (code, _) -> code == 0x11 }
        } ?: throw RuntimeException("cannot find marvelmind mobile beacon")

    private val buffer = ByteArray(32)

    override operator fun invoke() {
        port.readBytes(buffer, buffer.size.toLong())
            .takeIf { it > 0 }
            ?.let { buffer.asList().subList(0, it) }
            ?.let {
                engine(it) { (code, payload) ->
                    if (code != 0x11) return@engine
                    val value = ResolutionCoordinate(payload)
                    callback(System.currentTimeMillis() - value.delay,
                             value.x / 1000.0,
                             value.y / 1000.0)
                }
            }
    }

    override fun close() {
        port.closePort()
    }
}
