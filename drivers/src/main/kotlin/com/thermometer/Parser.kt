package com.thermometer

import cn.autolabor.serialport.parser.ParseEngine
import cn.autolabor.serialport.parser.ParseEngine.ParseInfo

data class Temperature(
    val temperature: Double,
    val humidity: Double)

internal fun engine() =
    ParseEngine<Byte, Temperature?> { buffer ->
        parse(buffer.toByteArray().toString(Charsets.US_ASCII))
    }

private const val PREFIX = "Temp-Inner:"
private const val INFIX = " [C],"
private const val POSTFIX = " [%RH]<\r\n"

private const val MIN_LENGTH = PREFIX.length + INFIX.length + 1

private fun parse(text: String): ParseInfo<Temperature?> {
    val size = text.length
    // 找前缀
    val head = text.indexOf(PREFIX.first()).takeUnless { it < 0 }
               ?: return ParseInfo(size, size, null)
    if (!text.substring(head).startsWith(PREFIX))
        return ParseInfo(head + 1, head + 1, null)
    // 找后缀
    val tail = text.indexOf(POSTFIX)
                   .takeIf { it > head + MIN_LENGTH }
                   ?.let { it + POSTFIX.length }
               ?: return ParseInfo(head, size, null)
    // 构造数据包
    val (t1, t2) =
        text.substring(head + PREFIX.length, tail - POSTFIX.length)
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
    return ParseInfo(tail, tail, Temperature(temperature, humidity))
}
