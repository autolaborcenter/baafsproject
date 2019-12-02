package com.faselase

import cn.autolabor.serialport.parser.ParseEngine
import cn.autolabor.serialport.parser.ParseEngine.ParseInfo
import kotlin.experimental.and
import kotlin.math.PI

private fun Byte.toIntUnsigned(): Int =
    if (this < 0) this + 256 else this.toInt()

private const val k_rho = 40.0 / 4096
private const val k_theta = 2 * PI / 5760

private val crcBits = intArrayOf(
        0, 1, 1, 2, 1, 2, 2, 3, 1, 2, 2, 3, 2, 3, 3, 4,
        1, 2, 2, 3, 2, 3, 3, 4, 2, 3, 3, 4, 3, 4, 4, 5,
        1, 2, 2, 3, 2, 3, 3, 4, 2, 3, 3, 4, 3, 4, 4, 5,
        2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,
        1, 2, 2, 3, 2, 3, 3, 4, 2, 3, 3, 4, 3, 4, 4, 5,
        2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,
        2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,
        3, 4, 4, 5, 4, 5, 5, 6, 4, 5, 5, 6, 5, 6, 6, 7,
        1, 2, 2, 3, 2, 3, 3, 4, 2, 3, 3, 4, 3, 4, 4, 5,
        2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,
        2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,
        3, 4, 4, 5, 4, 5, 5, 6, 4, 5, 5, 6, 5, 6, 6, 7,
        2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,
        3, 4, 4, 5, 4, 5, 5, 6, 4, 5, 5, 6, 5, 6, 6, 7,
        3, 4, 4, 5, 4, 5, 5, 6, 4, 5, 5, 6, 5, 6, 6, 7,
        4, 5, 5, 6, 5, 6, 6, 7, 5, 6, 6, 7, 6, 7, 7, 8)

private fun crcCheck(`package`: List<Byte>): Boolean {
    val value0 = (1..3).sumBy { crcBits[`package`[it].toIntUnsigned()] }.toByte() and 7
    val value1 = (`package`[0].toIntUnsigned() shr 4).toByte() and 7
    return value0 == value1
}

private fun Byte.takeBits(mask: Int, p: Int) =
    (toIntUnsigned() and mask).let { if (p >= 0) it shl p else it ushr -p }

internal sealed class LidarPack {
    object Nothing : LidarPack()
    object Failed : LidarPack()
    data class Invalid(val theta: Double) : LidarPack()
    data class Data(val rho: Double, val theta: Double) : LidarPack()
}

internal fun engine() =
    ParseEngine<Byte, LidarPack> { buffer: List<Byte> ->
        val size = buffer.size
        var begin =
            buffer
                .indexOfFirst { it >= 0 }
                .takeIf { it >= 0 }
            ?: return@ParseEngine ParseInfo(size, size, LidarPack.Nothing)

        val `package` =
            (begin + 4)
                .takeIf { it <= size }
                ?.let { buffer.subList(begin, it) }
            ?: return@ParseEngine ParseInfo(begin, size, LidarPack.Nothing)

        val result =
            if (crcCheck(`package`)) {
                begin += 4

                val rho = k_rho * (`package`[0].takeBits(0b00001111, 8)
                        or `package`[1].takeBits(0b01111111, 1)
                        or `package`[2].takeBits(0b01000000, -6))
                val theta = k_theta * (`package`[2].takeBits(0b00111111, 7)
                        or `package`[3].takeBits(0b01111111, 0))

                when {
                    theta !in 0.0..2 * PI -> LidarPack.Failed
                    rho !in .05..10.0     -> LidarPack.Invalid(theta)
                    else                  -> LidarPack.Data(rho, theta)
                }
            } else {
                begin += 1
                LidarPack.Failed
            }
        val (nextBegin, passed) =
            (begin until size)
                .find { buffer[it] >= 0 }
                ?.let { it to it + 1 }
            ?: size to size
        return@ParseEngine ParseInfo(nextBegin, passed, result)
    }
