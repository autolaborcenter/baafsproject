package com.marvelmind

import serialport.parser.Parser
import serialport.parser.Parser.ParseInfo

data class Package(val code: Int, val payload: List<Byte>) {
    companion object {
        val nothing = Package(-1, emptyList())
        val failed = Package(Int.MAX_VALUE, emptyList())
    }
}

/**
 * MarvelMind 移动节点网络层解析器
 */
class Parser : Parser<Byte, Package> {
    override operator fun invoke(buffer: List<Byte>): ParseInfo<Package> {
        val size = buffer.size
        // 找到一个帧头
        var begin = (0 until size - 1).find { i ->
            buffer[i] == DestinationAddress && buffer[i + 1] == PacketType
        } ?: return ParseInfo(size, size, Package.nothing)
        // 确定帧长度
        val `package` =
            begin.takeIf { it + 7 < size }
                ?.let { it + 7 + buffer[it + 4] }
                ?.takeIf { it < size }
                ?.let { buffer.subList(begin, it) }
            ?: return ParseInfo(begin, size, Package.nothing)
        // crc 校验
        val result =
            if (crc16Check(`package`)) {
                val payload = ArrayList<Byte>(`package`.size - 7)
                payload.addAll(`package`.subList(5, `package`.size - 2))
                begin += 7
                Package(`package`[3] * 255 + `package`[2], payload)
            } else {
                begin += 2
                Package.failed
            }
        // 找到下一个帧头
        val (nextBegin, passed) =
            (begin until size - 1)
                .find { buffer[it] == DestinationAddress && buffer[it + 1] == PacketType }
                ?.let { it to it + 1 }
            ?: (if (buffer.last() == DestinationAddress) size - 1 else size) to size

        return ParseInfo(nextBegin, passed, result)
    }
}
