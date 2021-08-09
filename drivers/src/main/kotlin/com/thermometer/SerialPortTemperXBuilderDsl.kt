package com.thermometer

import cn.autolabor.serialport.manager.SerialPortManager
import kotlinx.coroutines.channels.SendChannel
import org.mechdancer.SimpleLogger
import org.mechdancer.annotations.BuilderDslMarker
import org.mechdancer.common.Stamped
import org.mechdancer.exceptions.ExceptionMessage

@BuilderDslMarker
class SerialPortTemperXBuilderDsl
private constructor() {
    var portName: String? = null
    var period: Long = 1000L
    var dataTimeout: Long = 2000L

    var logger: SimpleLogger? = SimpleLogger("TemperX232")

    companion object {
        fun SerialPortManager.registerTemperX(
            temperatures: SendChannel<Stamped<Humiture>>,
            exceptions: SendChannel<ExceptionMessage>,
            block: SerialPortTemperXBuilderDsl.() -> Unit = {}
        ) =
            SerialPortTemperXBuilderDsl()
                .apply(block)
                .apply {
                    require(period > 0)
                    require(dataTimeout > period)
                }
                .run {
                    SerialPortTemperX(
                        temperatures = temperatures,
                        exceptions = exceptions,

                        portName = portName,
                        period = period,
                        dataTimeout = dataTimeout
                    ).also { it.logger = logger }
                }
                .also(this::register)
    }
}
