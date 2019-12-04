package com.usarthmi

import cn.autolabor.serialport.parser.ParseEngine

private const val head = 0x65.toByte()
private const val page = 0x00.toByte()
private const val end = 0xff.toByte()

internal enum class HMIPackage {
    Nothing, Failed, Button0, Button1, Button2
}

/** MarvelMind 移动节点网络层解析器 */
internal fun engine(): ParseEngine<Byte, HMIPackage> =
    ParseEngine { buffer ->
        val size = buffer.size
        // 找到一个帧头
        var begin = (0 until size - 1).find { i ->
            buffer[i] == head && buffer[i + 1] == page
        } ?: return@ParseEngine ParseEngine.ParseInfo(
                nextHead = (if (buffer.last() == head) size - 1 else size),
                nextBegin = size,
                result = HMIPackage.Nothing)
        // 确定帧长度
        val `package` =
            (begin + 7)
                .takeIf { it <= size }
                ?.let { buffer.subList(begin, it) }
            ?: return@ParseEngine ParseEngine.ParseInfo(
                    nextHead = begin,
                    nextBegin = size,
                    result = HMIPackage.Nothing)
        // crc 校验
        val result =
            if (`package`.takeLast(3).all { it == end }) {
                begin += 7
                when (`package`[2]) {
                    1.toByte() -> HMIPackage.Button0
                    2.toByte() -> HMIPackage.Button1
                    3.toByte() -> HMIPackage.Button2
                    else       -> HMIPackage.Failed
                }
            } else {
                begin += 2
                HMIPackage.Failed
            }
        // 找到下一个帧头
        return@ParseEngine ParseEngine.ParseInfo(begin, begin, result)
    }
