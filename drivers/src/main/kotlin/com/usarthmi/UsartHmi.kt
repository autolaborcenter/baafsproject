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

        val RECORD = HMIPackage.Info(1, 1, 1)
        val FOLLOW = HMIPackage.Info(1, 2, 1)
        val SHUT_DOWN = HMIPackage.Info(1, 3, 1)

        val SAVE_PATH = HMIPackage.Info(2, 1, 1)
        val CANCEL_RECORD = HMIPackage.Info(2, 2, 1)

        val CANCEL_FOLLOW = HMIPackage.Info(3, 5, 1)

        @Suppress("ObjectPropertyName", "NonAsciiCharacters", "Unused")
        const val 字库大 = "等待连接记录运行关闭保存退出正在异常发现障碍离开路线其他"
        @Suppress("ObjectPropertyName", "NonAsciiCharacters", "Unused")
        const val 字库小 = "0123456789点已保存"
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
                        RECORD            -> launch { msgFromHmi.send("record") }
                        FOLLOW            -> launch { msgFromHmi.send("load path\n'") }
                        SHUT_DOWN         -> launch { msgFromHmi.send("shut down") }
                        SAVE_PATH         -> launch { msgFromHmi.send("save path") }
                        CANCEL_FOLLOW,
                        CANCEL_RECORD     -> launch { msgFromHmi.send("cancel") }
                    }
                }
        }
    }
}
