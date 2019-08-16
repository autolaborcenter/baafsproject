package com.faselase

import cn.autolabor.Resource
import cn.autolabor.serialport.parser.SerialPortFinder

/**
 * 砝石雷达资源控制器
 * 用户可自选调度器，反复调用 [invoke] 方法以运行
 */
class Resource(
    private val callback: (Long, Long, List<Pair<Double, Double>>) -> Unit
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

    private val buffer = ByteArray(256)
    private var begin = System.currentTimeMillis()
    private var end = begin
    private var last = -1.0
    private val list = mutableListOf<Pair<Double, Double>>()

    override operator fun invoke() {
        port.readBytes(buffer, buffer.size.toLong())
            .takeIf { it > 0 }
            ?.let { buffer.asList().subList(0, it) }
            ?.let {
                engine(it) { (rho, theta) ->
                    if (rho <= 0) return@engine

                    val now = System.currentTimeMillis()
                    if (theta > last) {
                        end = now
                        list.add(rho to theta)
                    } else {
                        callback(begin, end, list)
                        begin = now
                        end = now
                        list.clear()
                        list.add(rho to theta)
                    }

                    last = theta
                }
            }
    }

    override fun close() {
        port.closePort()
    }
}
