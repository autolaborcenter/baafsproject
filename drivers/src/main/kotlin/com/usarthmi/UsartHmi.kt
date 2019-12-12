package com.usarthmi

import cn.autolabor.serialport.manager.Certificator
import cn.autolabor.serialport.manager.SerialPortDeviceBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch

class UsartHmi(
    portName: String?,
    private val msgFromHmi: SendChannel<String>
) : SerialPortDeviceBase("usart hmi", 9600, 32, portName) {
    private val engine = engine()
    private val output = Channel<String>()

    suspend fun write(msg: String) {
        output.send(msg)
    }

    private companion object {
        val END = listOf(0xff.toByte(), 0xff.toByte(), 0xff.toByte())
        val INIT = HMIPackage.Info(0, 0x55, 0xaa.toByte())
        val BUTTON0 = HMIPackage.Info(1, 0, 1)
        val BUTTON1 = HMIPackage.Info(1, 1, 1)
        val BUTTON2 = HMIPackage.Info(1, 2, 1)
        val BUTTON3 = HMIPackage.Info(1, 3, 1)

        @Suppress("ObjectPropertyName", "NonAsciiCharacters", "Unused")
        const val 字库 = "等待连接记录保存运行取消正在空闲请移除障碍"
    }

    override fun buildCertificator(): Certificator =
        object : CertificatorBase(1000L) {
            override val activeBytes = byteArrayOf()
            override fun invoke(bytes: Iterable<Byte>): Boolean? {
                var result = false
                engine(bytes) { result = result || it == INIT }
                return passOrTimeout(result)
            }
        }

    override fun setup(
        scope: CoroutineScope,
        toDevice: SendChannel<List<Byte>>,
        fromDevice: ReceiveChannel<List<Byte>>
    ) {
        scope.launch {
            for (text in output)
                text.toByteArray(Charsets.UTF_8)
                    .toMutableList()
                    .apply { addAll(END) }
                    .let { toDevice.send(it) }
        }
        scope.launch {
            for (bytes in fromDevice)
                engine(bytes) {
                    when (it) {
                        HMIPackage.Nothing,
                        HMIPackage.Failed -> Unit
                        BUTTON0           -> launch { msgFromHmi.send("record") }
                        BUTTON1           -> launch { msgFromHmi.send("save path") }
                        BUTTON2           -> launch { msgFromHmi.send("cancel") }
                        BUTTON3           -> launch { msgFromHmi.send("load path\n'") }
                    }
                }
        }
    }
}
