package cn.autolabor.autocan

import cn.autolabor.serialport.parser.ParseEngine
import cn.autolabor.serialport.parser.ParseEngine.ParseInfo
import cn.autolabor.serialport.parser.serial4Bytes.Serial4BytesInputStream

/** PM1 接收包 */
internal sealed class PM1Pack {
    object Nothing : PM1Pack()

    object Failed : PM1Pack()

    class WithData(
        val head: AutoCANPackageHead.WithData,
        val frameId: Byte,
        val data: ByteArray
    ) : PM1Pack()

    class WithoutData(
        val head: AutoCANPackageHead.WithoutData,
        val reserve: Byte
    ) : PM1Pack()
}

/** 构造解析引擎 */
internal fun engine() =
    ParseEngine<Byte, PM1Pack> { buffer: List<Byte> ->
        val size = buffer.size
        val begin =
            buffer
                .indexOfFirst { it == 0xfe.toByte() }
                .takeIf { it >= 0 }
                ?: return@ParseEngine ParseInfo(size, size, PM1Pack.Nothing)

        val stream =
            (begin + 4)
                .takeIf { it < size }
                ?.let { buffer.subList(begin + 1, it - 1) }
                ?.toByteArray()
                ?.let(::Serial4BytesInputStream)
                ?: return@ParseEngine ParseInfo(begin, size, PM1Pack.Nothing)

        val network = stream.readUnsigned(2).toByte()
        val dataField = stream.readUnsigned(1) > 0
        if (begin + (if (dataField) 14 else 6) > size)
            return@ParseEngine ParseInfo(begin, size, PM1Pack.Nothing)
        val priority = stream.readUnsigned(3).toByte()
        val nodeType = stream.readUnsigned(6).toByte()
        val nodeIndex = stream.readUnsigned(4).toByte()
        val messageType = buffer[begin + 3]

        val result = if (dataField) {
            val lastIndex = 13
            val head = AutoCANPackageHead.WithData(
                network, priority, nodeType, nodeIndex, messageType
            )
            val frameId = buffer[begin + 4]
            val data = buffer.subList(begin + 5, begin + lastIndex).toByteArray()
            if (head.pack(frameId, data).last() == buffer[begin + lastIndex])
                PM1Pack.WithData(head, frameId, data)
            else
                PM1Pack.Failed
        } else {
            val lastIndex = 5
            val head = AutoCANPackageHead.WithoutData(
                network, priority, nodeType, nodeIndex, messageType
            )
            val reserve = buffer[begin + 4]
            if (head.pack(reserve).last() == buffer[begin + lastIndex])
                PM1Pack.WithoutData(head, reserve)
            else
                PM1Pack.Failed
        }
        when (result) {
            PM1Pack.Nothing,
            PM1Pack.Failed         ->
                ParseInfo(begin + 1, begin + 1, PM1Pack.Failed)
            is PM1Pack.WithData    ->
                ParseInfo(begin + 14, begin + 14, result)
            is PM1Pack.WithoutData ->
                ParseInfo(begin + 6, begin + 6, result)
        }
    }
