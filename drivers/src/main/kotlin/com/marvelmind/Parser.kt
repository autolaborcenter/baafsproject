package com.marvelmind

import cn.autolabor.serialport.parser.ParseEngine
import cn.autolabor.serialport.parser.ParseEngine.ParseInfo
import com.marvelmind.BeaconPackage.*
import com.marvelmind.BeaconPackage.Nothing
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import kotlin.experimental.xor

private const val DestinationAddress = 0xff.toByte()
private const val PacketType = 0x47.toByte()

private fun Byte.toIntUnsigned(): Int =
    if (this < 0) this + 256 else this.toInt()

private fun crc16Check(list: List<Byte>): Boolean {
    var byteL: Byte = 0xff.toByte()
    var byteH: Byte = 0xff.toByte()
    for (it in list) {
        byteL = byteL xor it
        var short = (byteH.toIntUnsigned() shl 8) or byteL.toIntUnsigned()
        for (i in 0 until 8) {
            val odd = (short % 2) > 0
            short = short ushr 1
            if (odd) short = short xor 0xa001
        }
        byteH = (short ushr 8).toByte()
        byteL = short.toByte()
    }
    return byteH == 0.toByte() && byteL == 0.toByte()
}

internal class ResolutionCoordinate(private val list: ByteArray) {
    val timeStamp get() = build(0, 4)
    val x get() = build(4, 4).readInt()
    val y get() = build(8, 4).readInt()
    val z get() = build(12, 4).readInt()
    val flags get() = build(16, 1)
    val address get() = build(17, 1)
    val pair get() = build(18, 2)
    val delay get() = build(20, 2).readUnsignedShort()

    private fun build(offset: Int, length: Int) =
        list.copyOfRange(offset, offset + length)
            .reversedArray()
            .let(::ByteArrayInputStream)
            .let(::DataInputStream)
}

internal sealed class BeaconPackage {
    data class Nothing(val dropped: ByteArray) : BeaconPackage()
    data class Failed(val dropped: ByteArray) : BeaconPackage()
    data class Data(val code: Int, val payload: ByteArray) : BeaconPackage()
}

/** MarvelMind 移动节点网络层解析器 */
internal fun engine(): ParseEngine<Byte, BeaconPackage> = ParseEngine { buffer ->
    val size = buffer.size
    // 找到一个帧头
    var begin = (0 until size - 1).find { i ->
        buffer[i] == DestinationAddress && buffer[i + 1] == PacketType
    } ?: return@ParseEngine (if (buffer.last() == DestinationAddress) size - 1 else size)
        .let { drop ->
            ParseInfo(
                nextHead = drop,
                nextBegin = size,
                result = Nothing(buffer.subList(0, drop).toByteArray()))
        }
    // 确定帧长度
    val `package` = (begin + 7)
        .takeIf { it < size }
        ?.let { it + buffer[begin + 4].toIntUnsigned() }
        ?.takeIf { it < size }
        ?.let { buffer.subList(begin, it) }
        ?: return@ParseEngine ParseInfo(
            nextHead = begin,
            nextBegin = size,
            result = Nothing(buffer.subList(0, begin).toByteArray()))
    // crc 校验
    val result =
        if (crc16Check(`package`)) {
            begin += `package`.size
            Data(
                code = `package`[3].toIntUnsigned() * 256 + `package`[2].toIntUnsigned(),
                payload = `package`.subList(5, `package`.size - 2).toByteArray())
        } else {
            begin += 2
            Failed(buffer.subList(0, begin).toByteArray())
        }
    // 找到下一个帧头
    return@ParseEngine ParseInfo(begin, begin, result)
}
