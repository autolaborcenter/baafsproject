package com.marvelmind

import cn.autolabor.serialport.parser.ParseEngine
import cn.autolabor.serialport.parser.ParseEngine.ParseInfo
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

data class Package(val code: Int, val payload: List<Byte>) {
    companion object {
        val nothing = Package(-1, emptyList())
        val failed = Package(Int.MAX_VALUE, emptyList())
    }
}

/**
 * MarvelMind 移动节点网络层解析器
 */
fun parse(buffer: List<Byte>): ParseInfo<Package> {
    val size = buffer.size
    // 找到一个帧头
    var begin = (0 until size - 1).find { i ->
        buffer[i] == DestinationAddress && buffer[i + 1] == PacketType
    } ?: return ParseInfo(size, size, Package.nothing)
    // 确定帧长度
    val `package` =
        begin.takeIf { it + 7 < size }
            ?.let { it + 7 + buffer[it + 4] }
            ?.takeIf { it in 1 until size }
            ?.let { buffer.subList(begin, it) }
        ?: return ParseInfo(begin, size, Package.nothing)
    // crc 校验
    val result =
        if (crc16Check(`package`)) {
            val payload = ArrayList<Byte>(`package`.size - 7)
            payload.addAll(`package`.subList(5, `package`.size - 2))
            begin += 7
            Package(`package`[3] * 255 + `package`[2], payload)
        } else {
            begin += 2
            Package.failed
        }
    // 找到下一个帧头
    val (nextBegin, passed) =
        (begin until size - 1)
            .find { buffer[it] == DestinationAddress && buffer[it + 1] == PacketType }
            ?.let { it to it + 1 }
        ?: (if (buffer.last() == DestinationAddress) size - 1 else size) to size

    return ParseInfo(nextBegin, passed, result)
}

fun engine() = ParseEngine(::parse)
