package com.marvelmind.mobilebeacon

import cn.autolabor.serialport.parser.ParseEngine
import cn.autolabor.serialport.parser.ParseEngine.ParseInfo
import com.marvelmind.crc16Check
import com.marvelmind.mobilebeacon.BeaconPackage.*
import com.marvelmind.mobilebeacon.BeaconPackage.Nothing
import com.marvelmind.mobilebeacon.BeaconPackage.RawDistance.Distance
import com.marvelmind.readIntLE
import com.marvelmind.readShortLE
import com.marvelmind.toIntUnsigned
import java.io.ByteArrayInputStream
import java.io.InputStream

private const val DestinationAddress = 0xff.toByte()
private const val PacketType = 0x47.toByte()

private const val CODE_COORDINATE = 0x0011.toShort()
private const val CODE_RAW_DISTANCE = 0x0004.toShort()
private const val CODE_QUALITY = 0x0007.toShort()

internal sealed class BeaconPackage {
    object Nothing : BeaconPackage()
    object Failed : BeaconPackage()

    data class Coordinate(
        val timeStamp: Int,
        val x: Int,
        val y: Int,
        val z: Int,
        val flags: Byte,
        val address: Byte,
        val pair: Short,
        val delay: Short
    ) : BeaconPackage() {
        val available get() = flags % 2 == 0
    }

    data class RawDistance(
        val address: Byte,
        val d0: Distance,
        val d1: Distance,
        val d2: Distance,
        val d3: Distance,
        val timeStamp: Int,
        val delay: Short
    ) : BeaconPackage() {
        data class Distance(val address: Byte, val value: Int)
    }

    data class Quality(
        val address: Byte,
        val qualityPercent: Byte
    ) : BeaconPackage()

    data class Others(
        val code: Short
    ) : BeaconPackage()
}

/** MarvelMind 移动节点网络层解析器 */
internal fun engine(): ParseEngine<Byte, BeaconPackage> =
    ParseEngine { buffer ->
        val size = buffer.size
        // 找到一个帧头
        var begin = (0 until size - 1).find { i ->
            buffer[i] == DestinationAddress && buffer[i + 1] == PacketType
        } ?: return@ParseEngine ParseInfo(
            nextHead = (if (buffer.last() == DestinationAddress) size - 1 else size),
            nextBegin = size,
            result = Nothing
        )
        // 确定帧长度
        val `package` =
            (begin + 7)
                .takeIf { it < size }
                ?.let { it + buffer[begin + 4].toIntUnsigned() }
                ?.takeIf { it <= size }
                ?.let { buffer.subList(begin, it) }
                ?: return@ParseEngine ParseInfo(
                    nextHead = begin,
                    nextBegin = size,
                    result = Nothing
                )
        // crc 校验
        val result =
            if (crc16Check(`package`)) {
                begin += `package`.size
                val stream = ByteArrayInputStream(`package`.toByteArray())
                stream.readNBytes(2)
                val code = stream.readShortLE()
                stream.read()
                val payload = stream.readNBytes(`package`.size - 7)
                when (code) {
                    CODE_COORDINATE   -> {
                        //println("CODE_COORDINATE")
                        payload.toResolutionCoordinate()
                    }
                    CODE_RAW_DISTANCE -> {
                        //println("CODE_RAW_DISTANCE")
                        payload.toRawDistance()
                    }
                    CODE_QUALITY      -> {
                        //println("CODE_QUALITY")
                        payload.toQuality()
                    }
                    else              -> {
                        //println("Others")
                        Others(code)
                    }
                }
            } else {
                begin += 2
                Failed
            }
        return@ParseEngine ParseInfo(begin, begin, result)
    }

private fun ByteArray.toResolutionCoordinate() =
    ByteArrayInputStream(this).runCatching {
        use { stream ->
            Coordinate(
                timeStamp = stream.readIntLE(),
                x = stream.readIntLE(),
                y = stream.readIntLE(),
                z = stream.readIntLE(),
                flags = stream.read().toByte(),
                address = stream.read().toByte(),
                pair = stream.readShortLE(),
                delay = stream.readShortLE()
            )
        }
    }.getOrDefault(Failed)

private fun InputStream.readDistance() =
    Distance(read().toByte(), readIntLE()).also { read() }

private fun ByteArray.toRawDistance() =
    ByteArrayInputStream(this).runCatching {
        use { stream ->
            RawDistance(
                address = stream.read().toByte(),
                d0 = stream.readDistance(),
                d1 = stream.readDistance(),
                d2 = stream.readDistance(),
                d3 = stream.readDistance(),
                timeStamp = stream.readIntLE(),
                delay = stream.readShortLE()
            )
        }
    }.getOrDefault(Failed)

private fun ByteArray.toQuality() =
    ByteArrayInputStream(this).runCatching {
        use { stream ->
            Quality(
                address = stream.read().toByte(),
                qualityPercent = stream.read().toByte()
            )
        }
    }.getOrDefault(Failed)
