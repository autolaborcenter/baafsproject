package com.usarthmi

import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.mechdancer.exceptions.device.DeviceNotExistException

class UsartHmi(
    scope: CoroutineScope,
    private val msgFromHmi: SendChannel<String>,
    portName: String
) : CoroutineScope by scope {
    private val engine = engine()
    private val port =
        SerialPort.getCommPort(portName)
            .apply { baudRate = 9600 }
            .takeIf { it.openPort() }
        ?: throw DeviceNotExistException("USART HMI")

    fun write(msg: String) {
        val bytes = "log.txt=$msg".toByteArray(Charsets.US_ASCII)
        port.writeBytes(bytes, bytes.size.toLong())
        port.writeBytes(byteArrayOf(end, end, end), 3)
    }

    init {
        val buffer = ByteArray(64)
        launch {
            while (true) {
                val actual = port.readBytes(buffer, buffer.size.toLong())
                if (actual <= 0) continue
                engine(buffer.take(actual)) {
                    when (it) {
                        HMIPackage.Nothing,
                        HMIPackage.Failed  -> return@engine
                        HMIPackage.Button0 -> launch { msgFromHmi.send("load path") }
                        HMIPackage.Button1 -> launch { msgFromHmi.send("'") }
                        HMIPackage.Button2 -> launch { msgFromHmi.send("cancel") }
                    }
                }
            }
        }
    }

    private companion object {
        const val end = 0xff.toByte()
    }
}
