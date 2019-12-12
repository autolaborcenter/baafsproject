package com.thermometer

import cn.autolabor.serialport.parser.ParseEngine
import cn.autolabor.serialport.parser.ParseEngine.ParseInfo

private const val PREFIX = "Temp-Inner:"
private const val INFIX = " [C],"
private const val POSTFIX = " [%RH]<\r\n"

private const val MIN_LENGTH = PREFIX.length + INFIX.length + 1

internal fun engine() =
    ParseEngine<Byte, Pair<Double, Double>?> { buffer ->
        val size = buffer.size
        val text = buffer.toByteArray().toString(Charsets.US_ASCII)
        val head = text.indexOf(PREFIX.first())
                       .takeUnless { it < 0 }
                   ?: return@ParseEngine ParseInfo(size, size, null)
        val tail = text.indexOf(POSTFIX)
                       .takeUnless { it < 0 || it < head + MIN_LENGTH }
                       ?.let { it + POSTFIX.length }
                   ?: return@ParseEngine ParseInfo(head, size, null)
        val (t1, t2) = text.substring(head, tail - POSTFIX.length)
                           .takeIf { it.startsWith(PREFIX) }
                           ?.split(INFIX)
                           ?.takeIf { it.size == 2 }
                       ?: return@ParseEngine ParseInfo(head + 1, tail, null)
        val temp = t1.drop(PREFIX.length).toDoubleOrNull()
                   ?: return@ParseEngine ParseInfo(head + 1, tail, null)
        val humi = t2.toDoubleOrNull()
                   ?: return@ParseEngine ParseInfo(head + 1, tail, null)
        return@ParseEngine ParseInfo(tail, tail, temp to humi)
    }
