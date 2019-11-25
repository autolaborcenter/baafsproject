package cn.autolabor.serialport.parser.serial4Bytes

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

internal class Serial4BytesBuilder {
    private var buffer = 0

    private fun mask(begin: Int, size: Int) =
        when (size) {
            Int.SIZE_BITS     -> 0xffff
            Int.SIZE_BITS - 1 -> 0x7fff
            else              -> (1 shl size) - 1
        }.shl(Int.SIZE_BITS - begin - size)

    fun write(begin: Int, size: Int, value: Int) {
        require(begin in 0 until Int.SIZE_BITS)
        val end = begin + size
        require(end in 1..Int.SIZE_BITS)
        val m = mask(begin, size)
        val v = value shl Int.SIZE_BITS - end
        buffer = buffer and m.inv() or (v and m)
    }

    fun toByteArray() =
        ByteArrayOutputStream(4)
            .also { DataOutputStream(it).writeInt(buffer) }
            .toByteArray()
}
