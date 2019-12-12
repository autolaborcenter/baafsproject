package com.thermometer

import cn.autolabor.serialport.manager.Certificator
import cn.autolabor.serialport.manager.SerialPortDeviceBase
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.mechdancer.SimpleLogger
import org.mechdancer.WatchDog
import org.mechdancer.common.Stamped
import org.mechdancer.common.Stamped.Companion.stamp
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.exceptions.ExceptionMessage.Occurred
import org.mechdancer.exceptions.ExceptionMessage.Recovered
import org.mechdancer.exceptions.device.DataTimeoutException

class SerialPortTemperX
internal constructor(
    private val temperatures: SendChannel<Stamped<Temperature>>,
    private val exceptions: SendChannel<ExceptionMessage>,

    portName: String?,

    private val period: Long,
    dataTimeout: Long
) : SerialPortDeviceBase(NAME, 9600, 32, portName) {
    // 协议解析引擎
    private val engine = engine()
    // 超时异常监控
    private val dataTimeoutException =
        DataTimeoutException(NAME, dataTimeout)
    private val dataWatchDog =
        WatchDog(timeout = dataTimeout)
        { exceptions.send(Occurred(dataTimeoutException)) }

    var logger: SimpleLogger? = null

    private companion object {
        const val NAME = "temperx 232"
        val ACTIVE_BYTES = "ReadTemp".toByteArray(Charsets.US_ASCII)
    }

    override fun buildCertificator(): Certificator =
        object : CertificatorBase(2000L) {
            override val activeBytes get() = ACTIVE_BYTES

            override fun invoke(bytes: Iterable<Byte>): Boolean? {
                var result = false
                engine(bytes) { pair ->
                    pair?.also {
                        result = true
                        dataWatchDog.feed()
                        GlobalScope.launch { temperatures.send(stamp(it)) }
                    }
                }
                return passOrTimeout(result)
            }
        }

    override fun setup(
        scope: CoroutineScope,
        toDevice: SendChannel<List<Byte>>,
        fromDevice: ReceiveChannel<List<Byte>>
    ) {
        scope.launch {
            while (isActive) {
                delay(period)
                toDevice.send(ACTIVE_BYTES.asList())
            }
        }
        scope.launch {
            for (bytes in fromDevice)
                engine(bytes) { pair ->
                    pair?.also {
                        scope.launch {
                            temperatures.send(stamp(it))
                            exceptions.send(Recovered(dataTimeoutException))
                        }
                        dataWatchDog.feed()
                        logger?.log(pair)
                    }
                }
        }
    }
}
