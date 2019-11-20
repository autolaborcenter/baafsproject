package cn.autolabor.serial4Bytes

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream

internal class Serial4BytesParser(bits: ByteArray) {
    init {
        require(bits.size <= Int.SIZE_BYTES)
    }

    private val buffer =
        ByteArrayOutputStream(Int.SIZE_BYTES)
            .apply { writeBytes(bits) }
            .toByteArray()
            .let(::ByteArrayInputStream)
            .let(::DataInputStream)
            .readInt()

    fun readSigned(begin: Int, size: Int): Int {
        require(begin in 0 until Int.SIZE_BITS)
        require((begin + size) in 1..Int.SIZE_BITS)
        return buffer shl begin shr Int.SIZE_BITS - size
    }

    fun readUnsigned(begin: Int, size: Int): Int {
        require(begin in 0 until Int.SIZE_BITS)
        require((begin + size) in 1..Int.SIZE_BITS)
        return buffer shl begin ushr Int.SIZE_BITS - size
    }
}
