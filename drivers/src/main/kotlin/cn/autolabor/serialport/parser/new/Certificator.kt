package cn.autolabor.serialport.parser.new

internal interface Certificator {
    operator fun invoke(bytes: Iterable<Byte>): Boolean?
}
