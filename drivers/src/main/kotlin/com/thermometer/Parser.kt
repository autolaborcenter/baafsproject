package com.thermometer

import cn.autolabor.serialport.parser.ParseEngine
import cn.autolabor.serialport.parser.ParseEngine.ParseInfo

data class Humiture(
    val temperature: Double,
    val humidity: Double
)

internal fun engine() =
    ParseEngine<Byte, Humiture?> { buffer ->
        parse(buffer.toByteArray().toString(Charsets.US_ASCII))
    }

private const val PREFIX = "Temp-Inner:"
private const val INFIX = " [C],"
private const val POSTFIX = " [%RH]<\r\n"

private const val MIN_LENGTH = PREFIX.length + INFIX.length + 1

private fun parse(text: String): ParseInfo<Humiture?> {
    val size = text.length
    // 找前缀
    val head = text.indexOf(PREFIX.first()).takeUnless { it < 0 }
        ?: return ParseInfo(size, size, null)
    val maybe = text.substring(head)
    when {
        maybe.length < PREFIX.length ->
            return ParseInfo(head, size, null)
        !maybe.startsWith(PREFIX)    ->
            return ParseInfo(head + 1, head + 1, null)
    }
    // 找后缀
    val tail = maybe.indexOf(POSTFIX)
        .takeIf { it > MIN_LENGTH }
        ?.let { it + head + POSTFIX.length }
        ?: return ParseInfo(head, size, null)
    // 构造数据包
    val (t1, t2) =
        maybe.substring(PREFIX.length, tail - head - POSTFIX.length)
            .split(INFIX)
            .takeIf { it.size == 2 }
            ?: return ParseInfo(head + PREFIX.length, tail, null)
    // 解析数字
    val temperature =
        t1.toDoubleOrNull()
            ?: return ParseInfo(tail, tail, null)
    val humidity =
        t2.toDoubleOrNull()
            ?: return ParseInfo(tail, tail, null)
    // 成功
    return ParseInfo(tail, tail, Humiture(temperature, humidity))
}
