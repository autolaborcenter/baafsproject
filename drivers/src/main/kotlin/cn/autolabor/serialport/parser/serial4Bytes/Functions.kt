package cn.autolabor.serialport.parser.serial4Bytes

internal fun buildSerial4Bytes(block: Serial4BytesOutputStream.() -> Unit): ByteArray =
    Serial4BytesOutputStream().apply(block).toByteArray()
