package cn.autolabor.serial4Bytes

internal class Serial4BytesOutputStream {
    private val builder = Serial4BytesBuilder()
    private var i = 0

    fun write(size: Int, value: Int) {
        if (value != 0) builder.write(i, size, value)
        i += size
    }

    fun skip(size: Int) {
        i += size
    }

    fun toByteArray() =
        builder.toByteArray()
}
