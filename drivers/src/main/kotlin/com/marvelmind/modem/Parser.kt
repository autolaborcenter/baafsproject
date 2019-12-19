package com.marvelmind.modem

import cn.autolabor.serialport.parser.ParseEngine
import cn.autolabor.serialport.parser.ParseEngine.ParseInfo
import com.marvelmind.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

internal sealed class Command(
    private val priority: Int
) : Comparable<Command> {
    abstract val address: Byte  // 地址
    abstract val data: ByteArray

    override fun compareTo(other: Command) =
        priority.compareTo(other.priority)

    // 读取版本指令
    class CommandVersionR(
        override val address: Byte
    ) : Command(0) {
        override val data: ByteArray by lazy {
            ByteArrayOutputStream()
                .apply {
                    write(address.toInt())
                    write(0x03)
                    writeLE(0xFE00.toShort())
                    writeLE(0x0000.toShort())
                    write(crc16(toByteArray()))
                }
                .toByteArray()
        }
    }

    // 读取配置指令（温度）
    class CommandConfigR(
        override val address: Byte
    ) : Command(0) {
        override val data: ByteArray by lazy {
            ByteArrayOutputStream()
                .apply {
                    write(address.toInt())
                    write(0x03)
                    writeLE(0x5000.toShort())
                    writeLE(0x0000.toShort())
                    write(crc16(toByteArray()))
                }
                .toByteArray()
        }
    }

    // 写配置指令(温度)
    class CommandConfigW(
        override val address: Byte,
        configData: ByteArray
    ) : Command(0) {
        override val data: ByteArray by lazy {
            ByteArrayOutputStream()
                .apply {
                    write(address.toInt())
                    write(0x10)
                    writeLE(0x5000.toShort())
                    writeLE(0x0000.toShort())
                    write(0x30)
                    write(configData)
                    write(crc16(toByteArray()))
                }
                .toByteArray()
        }
    }

    // 读取原始距离指令
    class CommandRawDistanceR(
        override val address: Byte
    ) : Command(1) {
        override val data: ByteArray by lazy {
            ByteArrayOutputStream()
                .apply {
                    write(address.toInt())
                    write(0x03)
                    writeLE(0x4000.toShort())
                    writeLE(0x0000.toShort())
                    write(crc16(toByteArray()))
                }
                .toByteArray()
        }
    }

    // 读取标签状态指令
    class CommandStateR(
        override val address: Byte
    ) : Command(2) {
        override val data: ByteArray by lazy {
            ByteArrayOutputStream()
                .apply {
                    write(address.toInt())
                    write(0x03)
                    writeLE(0x0003.toShort())
                    writeLE(0x0002.toShort())
                    write(crc16(toByteArray()))
                }
                .toByteArray()
        }
    }

    // 唤醒标签指令
    class CommandWakeW(
        override val address: Byte
    ) : Command(0) {
        override val data: ByteArray by lazy {
            ByteArrayOutputStream()
                .apply {
                    write(address.toInt())
                    write(0x10)
                    writeLE(0xb006.toShort())
                    writeLE(0x0002.toShort())
                    write(0x08)
                    writeLE(0x815e942d.toInt())
                    writeLE(0x00000002)
                    write(crc16(toByteArray()))
                }
                .toByteArray()
        }
    }

    // 读取submap指令
    class CommandSubmapR(
        override val address: Byte
    ) : Command(0) {
        override val data: ByteArray by lazy {
            ByteArrayOutputStream()
                .apply {
                    write(0xFF)
                    write(0x03)
                    writeLE((0x6000 + address.toIntUnsigned()).toShort())
                    writeLE(0x0000.toShort())
                    write(crc16(toByteArray()))
                }
                .toByteArray()
        }
    }

    // 写submap指令
    class CommandSubmapW(
        override val address: Byte,
        submapData: ByteArray
    ) : Command(0) {
        override val data: ByteArray by lazy {
            ByteArrayOutputStream()
                .apply {
                    write(0xFF)
                    write(0x10)
                    writeLE((0x6000 + address.toIntUnsigned()).toShort())
                    write(0)
                    write(0)
                    write(0x50)
                    write(submapData)
                    write(crc16(toByteArray()))
                }
                .toByteArray()
        }
    }

    // 写固定标签坐标指令
    class CommandCoordinateW(
        override val address: Byte,
        coordinateData: ByteArray
    ) : Command(0) {
        override val data: ByteArray by lazy {
            ByteArrayOutputStream()
                .apply {
                    write(address.toInt())
                    write(0x10)
                    writeLE(0x5003.toShort())
                    writeLE(0x0002.toShort())
                    write(0x20)
                    write(coordinateData)
                    write(0xff)
                    repeat(12) { write(0) }
                    write(0x02)
                    repeat(6) { write(0) }
                    write(crc16(toByteArray()))
                }
                .toByteArray()
        }
    }
}

// 标签设备
internal class Device(
    val id: Byte
) {
    var voltage = -1
    var sleep = false
    val cmd = Command.CommandStateR(id)
    // 解析
    fun parse(data: ByteArray) {
        ByteArrayInputStream(data.copyOfRange(7, 9))
            .use { stream ->
                voltage = (stream.readShortLE().toInt() and 0x1FFF) // TODO 协议为0x0FFF但这样最大只能表示到4.096V
            }
        ByteArrayInputStream(data.copyOfRange(4, 9))
            .use { stream ->
                val rssi = stream.read().toByte().toIntUnsigned()
                val unk = stream.read().toByte().toIntUnsigned()
                val temp = stream.read().toByte().toIntUnsigned()
                val volL = stream.read().toByte().toIntUnsigned()
                val volH = stream.read().toByte().toIntUnsigned()
                sleep = (rssi + 1 == unk
                         && unk + 1 == temp
                         && temp + 1 == volL
                         && volL + 1 == volH)
                        || (rssi == 0
                            && unk == 0
                            && temp == 0
                            && volL == 0
                            && volH == 0)
            }
    }

    // 打印
    override fun toString() =
        "beacon${id}:   ${voltage / 1000.0}V"
}

// 地图
internal class Map(pathName: String) {
    var filled = false
    val submaps = mutableListOf<ByteArray>()
    val beacons = mutableListOf<Pair<Byte, ByteArray>>()
    init {
        File(pathName)
            .takeIf(File::exists)
            ?.readBytes()
            ?.takeIf { it.isNotEmpty() && it.size % 81 == 0 }
            ?.let {
                for (i in it.indices step 81)
                    if (it[i] == 0.toByte())
                        submaps.add(it.copyOfRange(i + 1, i + 81))
                    else if (it[i] == 1.toByte())
                        for (j in 1..80 step 14)
                            if (it[i + j].toIntUnsigned() > 0)
                                beacons.add(Pair(it[i + j], it.copyOfRange(i + j + 1, i + j + 13)))
                            else
                                break
                if (submaps.size > 0 && beacons.size > 0)
                    filled = true
            }
    }
}

internal sealed class DataPackage {
    object Nothing : DataPackage()
    object Failed : DataPackage()
    class Data(private val type: Int, private val payload: ByteArray) : DataPackage() {
        operator fun component1() = type
        operator fun component2() = payload
    }
}

/** MarvelMind 网络层解析器 */
internal fun engine(): ParseEngine<Byte, DataPackage> =
    ParseEngine { buffer ->
        val size = buffer.size
        var headLen = 0
        // 找到一个帧头
        var begin = 0
        for (i in 0 until size - 2) {
            if (buffer[i] == 0xFF.toByte() && buffer[i + 1] == 0x47.toByte()) {           // 广播包
                headLen = 5
                begin = i
                break
            } else if (buffer[i] == 0xFF.toByte() && buffer[i + 1] == 0x03.toByte()) {    // 路由请求返回包
                headLen = 3
                begin = i
                break
            } else if (buffer[i + 1] == 0x03.toByte() && buffer[i + 2] == 0x20.toByte()) { // 通过路由读标签状态返回包
                headLen = 3
                begin = i
                break
            }
        }
        if (headLen == 0)
            return@ParseEngine ParseInfo(
                    nextHead = if (size >= 2) size - 2 else 0,
                    nextBegin = size,
                    result = DataPackage.Nothing)
        // 确定帧长度
        val `package` =
            (begin + headLen + 2)
                .takeIf { it < size }
                ?.let { it + buffer[begin + headLen - 1].toIntUnsigned() }
                ?.takeIf { it <= size }
                ?.let { buffer.subList(begin, it) }
            ?: return@ParseEngine ParseInfo(
                    nextHead = begin,
                    nextBegin = size,
                    result = DataPackage.Nothing)
        // crc 校验
        val result =
            if (crc16Check(`package`)) {
                begin += `package`.size
                DataPackage.Data(
                        `package`[2].toIntUnsigned(),
                        `package`.subList(headLen, `package`.size - 2).toByteArray())
            } else {
                begin += 2
                DataPackage.Failed
            }
        // 找到下一个帧头
        return@ParseEngine ParseInfo(begin, begin, result)
    }

