package com.marvelmind

import cn.autolabor.serialport.parser.ParseEngine
import cn.autolabor.serialport.parser.ParseEngine.ParseInfo
import java.io.ByteArrayInputStream

private const val DestinationAddress = 0xff.toByte()
private const val PacketType = 0x47.toByte()

internal class ResolutionCoordinate(list: ByteArray) {
    val timeStamp: Int
    val x: Int
    val y: Int
    val z: Int
    val flags: Byte
    val address: Byte
    val pair: Short
    val delay: Short

    init {
        val stream = ByteArrayInputStream(list)

        timeStamp = stream.readIntLE()
        x = stream.readIntLE()
        y = stream.readIntLE()
        z = stream.readIntLE()
        flags = stream.read().toByte()
        address = stream.read().toByte()
        pair = stream.readShortLE()
        delay = stream.readShortLE()
    }
}

internal sealed class BeaconPackage {
    object Nothing : BeaconPackage()
    object Failed : BeaconPackage()
    class Data(val code: Int, val payload: ByteArray) : BeaconPackage() {
        operator fun component1() = code
        operator fun component2() = payload
    }
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
                result = BeaconPackage.Nothing)
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
                    result = BeaconPackage.Nothing)
        // crc 校验
        val result =
            if (crc16Check(`package`)) {
                begin += `package`.size
                BeaconPackage.Data(
                        code = `package`[3].toIntUnsigned() * 256 + `package`[2].toIntUnsigned(),
                        payload = `package`.subList(5, `package`.size - 2).toByteArray())
            } else {
                begin += 2
                BeaconPackage.Failed
            }
        // 找到下一个帧头
        return@ParseEngine ParseInfo(begin, begin, result)
    }
