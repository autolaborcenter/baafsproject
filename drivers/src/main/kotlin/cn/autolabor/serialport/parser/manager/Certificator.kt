package cn.autolabor.serialport.parser.manager

internal interface Certificator {
    operator fun invoke(bytes: Iterable<Byte>): Boolean?
}
