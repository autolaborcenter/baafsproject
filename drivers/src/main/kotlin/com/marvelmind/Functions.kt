package com.marvelmind

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import kotlin.experimental.xor

internal fun Byte.toIntUnsigned(): Int =
    if (this < 0) this + 256 else this.toInt()

internal fun shortLEOf(b0: Byte, b1: Byte) =
    (b1.toInt() shl 8 or (b0.toInt() and 0xff)).toShort()

internal fun shortLEOfU(b0: Byte, b1: Byte) =
    shortLEOf(b0, b1).let { if (it >= 0) it.toInt() else it + 65536 }

// 读取一个小端短整型
internal fun InputStream.readShortLE(): Short {
    val b1: Int = read()
    val b2: Int = read()
    if (b1 or b2 < 0) throw EOFException()
    return ((b2 shl 8) or b1).toShort()
}

// 读取一个小端整型
internal fun InputStream.readIntLE(): Int {
    val b1: Int = read()
    val b2: Int = read()
    val b3: Int = read()
    val b4: Int = read()
    if (b1 or b2 or b3 or b4 < 0) throw EOFException()
    return (b4 shl 24) or (b3 shl 16) or (b2 shl 8) or b1
}

internal fun OutputStream.writeLE(value: Short) {
    var java = value.toInt()
    for (i in 1..Short.SIZE_BYTES) {
        write(java and 0xff)
        java = java shr 8
    }
}

internal fun OutputStream.writeLE(value: Int) {
    var java = value
    for (i in 1..Int.SIZE_BYTES) {
        write(java and 0xff)
        java = java shr 8
    }
}

// 计算 crc
internal fun crc16(list: ByteArray): ByteArray {
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
    return byteArrayOf(byteL, byteH)
}

// crc 校验
internal fun crc16Check(list: List<Byte>): Boolean {
    val (crc0, crc1) = crc16(list.toByteArray())
    return crc0 == 0.toByte() && crc1 == 0.toByte()
}
