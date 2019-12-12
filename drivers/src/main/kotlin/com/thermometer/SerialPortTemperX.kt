package com.thermometer

import cn.autolabor.serialport.manager.Certificator
import cn.autolabor.serialport.manager.OpenCondition.Certain
import cn.autolabor.serialport.manager.OpenCondition.None
import cn.autolabor.serialport.manager.SerialPortDevice
import cn.autolabor.serialport.manager.durationFrom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

class SerialPortTemperX
internal constructor(
    portName: String?
) : SerialPortDevice {
    private val engine = engine()

    override val tag = "temperx 232"

    override val openCondition = portName?.let { Certain(it) } ?: None
    override val baudRate = 9600
    override val bufferSize = 64

    private companion object {
        val ACTIVE_BYTES = "ReadTemp".toByteArray(Charsets.US_ASCII)
    }

    private var lastReceive = 0L

    override fun buildCertificator() =
        object : Certificator {
            override val activeBytes get() = ACTIVE_BYTES

            private val t0 = System.currentTimeMillis()
            override fun invoke(bytes: Iterable<Byte>): Boolean? {
                var result = false
                engine(bytes) { pair ->
                    if (pair != null) {
                        lastReceive = System.currentTimeMillis()
                        result = true
                    }
                }
                return when {
                    result                   -> true
                    durationFrom(t0) > 2000L -> false
                    else                     -> null
                }
            }
        }

    override fun setup(
        scope: CoroutineScope,
        toDevice: SendChannel<List<Byte>>,
        fromDevice: ReceiveChannel<List<Byte>>
    ) {
        scope.launch {
            while (isActive) {
                delay(max(1L, lastReceive + 2000L - System.currentTimeMillis()))
                toDevice.send(ACTIVE_BYTES.asList())
                delay(100L)
            }
        }
        scope.launch {
            for (bytes in fromDevice)
                engine(bytes) { pair ->
                    if (pair != null) {
                        lastReceive = System.currentTimeMillis()
                        println(pair)
                    }
                }
        }
    }
}
