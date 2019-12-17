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

    enum class Page(private val text: String, internal val pack: HMIPackage.Info) {
        Waiting("waiting", PAGE_WAITING),
        Index("index", PAGE_INDEX),
        Record("record", PAGE_RECORD),
        Follow("follow", PAGE_FOLLOW);

        fun toPack() = "page $text".toCommand()
    }

    var page = Page.Waiting

    suspend fun write(msg: String) {
        output.send(msg)
    }

    private companion object {
        val END = listOf(0xff.toByte(), 0xff.toByte(), 0xff.toByte())
        val PAGE_WAITING = HMIPackage.Info(0, 0, 0.toByte())
        val PAGE_INDEX = HMIPackage.Info(1, 0, 0.toByte())
        val PAGE_RECORD = HMIPackage.Info(2, 0, 0.toByte())
        val PAGE_FOLLOW = HMIPackage.Info(3, 0, 0.toByte())

        val pageClock = setOf(PAGE_WAITING, PAGE_INDEX, PAGE_RECORD, PAGE_FOLLOW)

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

        fun String.toCommand() = (toByteArray(Charsets.UTF_8) + END).asList()
    }

    override fun buildCertificator(): Certificator =
        object : CertificatorBase(1000L) {
            override val activeBytes = byteArrayOf()
            override fun invoke(bytes: Iterable<Byte>): Boolean? {
                var result = false
                engine(bytes) { result = result || it in pageClock }
                return passOrTimeout(result)
            }
        }

    override fun setup(
        scope: CoroutineScope,
        toDevice: SendChannel<List<Byte>>,
        fromDevice: ReceiveChannel<List<Byte>>
    ) {
        scope.launch { for (text in output) toDevice.send(text.toCommand()) }
        scope.launch {
            for (bytes in fromDevice)
                engine(bytes) {
                    when (it) {
                        HMIPackage.Nothing,
                        HMIPackage.Failed -> Unit
                        Page.Waiting.pack -> launch { if (page != Page.Waiting) toDevice.send(page.toPack()) }
                        Page.Index.pack   -> launch { if (page != Page.Index) toDevice.send(page.toPack()) }
                        Page.Record.pack  -> launch { if (page != Page.Record) toDevice.send(page.toPack()) }
                        Page.Follow.pack  -> launch { if (page != Page.Follow) toDevice.send(page.toPack()) }
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
