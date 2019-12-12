package com.usarthmi

import cn.autolabor.serialport.manager.SerialPortManager
import kotlinx.coroutines.channels.SendChannel
import org.mechdancer.annotations.BuilderDslMarker

@BuilderDslMarker
class UsartHmiBuilderDsl
private constructor() {
    var portName: String? = null

    companion object {
        fun SerialPortManager.registerUsartHmi(
            messages: SendChannel<String>,
            block: UsartHmiBuilderDsl.() -> Unit = {}
        ) =
            UsartHmiBuilderDsl()
                .apply(block)
                .run { UsartHmi(portName, messages) }
                .also(this::register)
    }
}
