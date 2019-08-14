package com.marvelmind

import kotlin.experimental.xor

const val DestinationAddress = 0xff.toByte()
const val PacketType = 0x47.toByte()

fun Byte.toIntUnsigned(): Int =
    if (this < 0) this + 256 else this.toInt()

fun crc16Check(list: List<Byte>): Boolean {
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
    val x get() = build(4, 4)
    val y get() = build(8, 4)
    val z get() = build(12, 4)
    val flags get() = build(16, 1)
    val address get() = build(17, 1)
    val pair get() = build(18, 2)
    val delay get() = build(20, 2)

    private fun build(offset: Int, length: Int): Long {
        var value = 0.toLong()
        for (i in offset + length - 1 downTo offset)
            value = value * 256 + list[i]
        return value
    }
}
