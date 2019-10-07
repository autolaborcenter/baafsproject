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

class ResolutionCoordinate(private val list: List<Byte>) {
    val timeStamp get() = build(0, 4)
    val x get() = build(4, 4).readInt()
    val y get() = build(8, 4).readInt()
    val z get() = build(12, 4).readInt()
    val flags get() = build(16, 1)
    val address get() = build(17, 1)
    val pair get() = build(18, 2)
    val delay get() = build(20, 2).readUnsignedShort()

    private fun build(offset: Int, length: Int) =
        list.subList(offset, offset + length)
            .toByteArray()
            .reversedArray()
            .let(::ByteArrayInputStream)
            .let(::DataInputStream)
}

sealed class BeaconPackage {
    object Nothing : BeaconPackage()
    object Failed : BeaconPackage()
    data class Data(val code: Int, val payload: List<Byte>) : BeaconPackage()
}

/** MarvelMind 移动节点网络层解析器 */
fun engine(): ParseEngine<Byte, BeaconPackage> = ParseEngine { buffer ->
    val size = buffer.size
    // 找到一个帧头
    var begin = (0 until size - 1).find { i ->
        buffer[i] == DestinationAddress && buffer[i + 1] == PacketType
    } ?: return@ParseEngine ParseInfo(
        nextHead =
        if (buffer.last() == DestinationAddress)
            size - 1
        else
            size,
        nextBegin = size,
        result = Nothing
    )
    // 确定帧长度
    val `package` =
        begin.takeIf { it + 7 < size }
            ?.let { it + 7 + buffer[it + 4] }
            ?.takeIf { it in 1 until size }
            ?.let { buffer.subList(begin, it) }
        ?: return@ParseEngine ParseInfo(begin, size, Nothing)
    // crc 校验
    val result =
        if (crc16Check(`package`)) {
            val payload = ArrayList<Byte>(`package`.size - 7)
            payload.addAll(`package`.subList(5, `package`.size - 2))
            begin += `package`.size
            Data(`package`[3].toIntUnsigned() * 256 + `package`[2].toIntUnsigned(), payload)
        } else {
            begin += 2
            Failed
        }
    // 找到下一个帧头
    return@ParseEngine ParseInfo(begin, begin, result)
}
