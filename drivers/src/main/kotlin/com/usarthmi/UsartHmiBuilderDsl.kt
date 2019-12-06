package com.usarthmi

import cn.autolabor.serialport.manager.SerialPortManager
import kotlinx.coroutines.channels.SendChannel

fun SerialPortManager.usartHmi(
    portName: String,
    messages: SendChannel<String>
) =
    UsartHmi(portName, messages)
        .also(this::register)
