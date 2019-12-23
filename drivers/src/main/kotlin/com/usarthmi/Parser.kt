package com.usarthmi

import cn.autolabor.serialport.parser.ParseEngine
import cn.autolabor.serialport.parser.ParseEngine.ParseInfo

private const val head = 0x65.toByte()
private const val end = 0xff.toByte()

internal sealed class HMIPackage {
    object Nothing : HMIPackage()
    object Failed : HMIPackage()
    data class Info(
        val page: Byte,
        val control: Byte,
        val action: Byte
    ) : HMIPackage()
}

/** MarvelMind 移动节点网络层解析器 */
internal fun engine(): ParseEngine<Byte, HMIPackage> =
    ParseEngine { buffer ->
        val size = buffer.size
            // 找到一个帧头
        var head = buffer.indexOfFirst { it == head }.takeUnless { it < 0 }
                   ?: return@ParseEngine ParseInfo(size, size, HMIPackage.Nothing)
        // 确定帧长度
        val `package` = (head + 7).takeIf { it <= size }
                            ?.let { buffer.subList(head, it) }
                        ?: return@ParseEngine ParseInfo(head, size, HMIPackage.Nothing)
        // 校验
        val result =
            if (`package`.takeLast(3).all { it == end }) {
                head += 7
                HMIPackage.Info(`package`[1], `package`[2], `package`[3])
            } else {
                head += 1
                HMIPackage.Failed
            }
        // 找到下一个帧头
        return@ParseEngine ParseInfo(head, head, result)
    }
