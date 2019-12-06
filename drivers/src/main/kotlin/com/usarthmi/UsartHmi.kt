package com.usarthmi

import cn.autolabor.serialport.manager.Certificator
import cn.autolabor.serialport.manager.OpenCondition.Certain
import cn.autolabor.serialport.manager.SerialPortDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch

class UsartHmi(
    portName: String,
    private val msgFromHmi: SendChannel<String>
) : SerialPortDevice {
    override val tag = "UsartHmi"
    override val openCondition = Certain(portName)
    override val baudRate = 9600
    override val bufferSize = 64

    private val engine = engine()
    private val output = Channel<String>()

    override fun buildCertificator(): Certificator? = null
    override fun setup(
        scope: CoroutineScope,
        toDevice: SendChannel<List<Byte>>,
        fromDevice: ReceiveChannel<List<Byte>>
    ) {
        scope.launch {
            val end = listOf(0xff.toByte(), 0xff.toByte(), 0xff.toByte())
            for (text in output)
                text.toByteArray(Charsets.US_ASCII)
                    .toMutableList()
                    .apply { addAll(end) }
                    .let { toDevice.send(it) }
        }
        scope.launch {
            for (bytes in fromDevice) {
                engine(bytes) {
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

    suspend fun write(msg: String) {
        output.send("log.txt=\"$msg\"")
    }
}
