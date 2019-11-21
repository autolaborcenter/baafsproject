package cn.autolabor.serialport.parser.serial4Bytes

internal class Serial4BytesInputStream(bits: ByteArray) {
    private val parser = Serial4BytesParser(bits)
    private var i = 0

    fun readSigned(size: Int) =
        parser.readSigned(i, size).also { i += size }

    fun readUnsigned(size: Int) =
        parser.readUnsigned(i, size).also { i += size }

    fun skip(size: Int) {
        i += size
    }
}
