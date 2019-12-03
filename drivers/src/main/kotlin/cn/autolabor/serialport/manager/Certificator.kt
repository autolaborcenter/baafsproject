package cn.autolabor.serialport.manager

interface Certificator {
    val activeBytes: ByteArray
    operator fun invoke(bytes: Iterable<Byte>): Boolean?
}
