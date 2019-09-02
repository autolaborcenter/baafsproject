package com.faselase

import cn.autolabor.Resource
import cn.autolabor.Stamped
import cn.autolabor.serialport.parser.SerialPortFinder

/**
 * 砝石雷达资源控制器
 * 用户可自选调度器，反复调用 [invoke] 方法以运行
 */
class Resource(
    private val callback: (List<Stamped<Polar>>) -> Unit
) : Resource {

    private val engine = engine(filter = true)
    private val port =
        SerialPortFinder.findSerialPort(engine) {
            baudRate = 460800
            timeoutMs = 5000
            bufferSize = 32
            activate = "#SF 10\r\n".toByteArray(Charsets.US_ASCII)
            condition { (rho, _) -> rho > 0 }
        } ?: throw RuntimeException("cannot find faselase lidar")
    override val info: String
        get() = port.descriptivePortName

    private val buffer = ByteArray(256)
    private val list = mutableListOf<Stamped<Polar>>()

    override operator fun invoke() {
        port.readBytes(buffer, buffer.size.toLong())
            .takeIf { it > 0 }
            ?.let { buffer.asList().subList(0, it) }
            ?.let { buffer ->
                engine(buffer) { (rho, theta) ->
                    if (rho <= 0) return@engine

                    list.add(Stamped(System.currentTimeMillis(), Polar(rho, theta)))
                    while (true) {
                        if (theta > list.firstOrNull()?.data?.angle ?: Double.MAX_VALUE)
                            list.removeAt(0)
                        else break
                    }
                    callback(list)
                }
            }
    }

    override fun close() {
        port.closePort()
    }
}
