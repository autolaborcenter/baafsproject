package com.marvelmind

import cn.autolabor.serialport.parser.ParseEngine
import cn.autolabor.serialport.parser.ParseEngine.ParseInfo
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

// 指令
internal class Command(
    val address: Byte,
    type: Byte,
    val code: Short,
    access: Short
) {
    val data = ByteArrayOutputStream()
        .apply {
            write(address.toInt())
            write(type.toInt())
            writeLE(code)
            writeLE(access)
            write(crc16(toByteArray()))
        }
        .toByteArray()
}

// 指令(写Submap)
internal class CommandWS(
    address: Byte,
    submap: ByteArray
) {
    val data = ByteArrayOutputStream()
        .apply {
            write(0xff)
            write(0x10)
            writeLE((0x6000 + address.toIntUnsigned()).toShort())
            write(0)
            write(0)
            write(0x50)
            write(submap)
            write(crc16(toByteArray()))
        }
        .toByteArray()
}

// 指令(写坐标)
internal class CommandWP(
    address: Byte,
    position: ByteArray
) {
    val data = ByteArrayOutputStream()
        .apply {
            write(address.toInt())
            write(0x10)
            writeLE(0x5003.toShort())
            writeLE(0x0002.toShort())
            write(0x20)
            write(position)
            write(0xff)
            repeat(12) { write(0) }
            write(0x02)
            repeat(6) { write(0) }
            write(crc16(toByteArray()))
        }
        .toByteArray()
}

// 一次定位结果
internal class Position(
    var address: Byte = 0.toByte(),
    var x: Int = 0,
    var y: Int = 0,
    var z: Int = 0,
    var usable: Boolean = true,
    var delay: Int = 0,
    var quality: Int = -1,
    val distances: MutableList<Pair<Byte, Int>> = mutableListOf()
) {
    // 接收计数
    var positionCnt = 0
    var qualityCnt = 0
    var distanceCnt = 0
    // 设置默认值
    fun setDefault() {
        address = 0
        x = 0
        y = 0
        z = 0
        usable = true
        delay = 0
        quality = -1
        distances.clear()
        positionCnt = 0
        qualityCnt = 0
        distanceCnt = 0
    }

    // 解析定位坐标数据
    fun parsePosition(data: ByteArray) {
        val cnt = positionCnt
        setDefault()
        positionCnt = cnt + 1

        ByteArrayInputStream(data)
            .use { stream ->
                stream.readNBytes(4)
                x = stream.readIntLE()
                y = stream.readIntLE()
                z = stream.readIntLE()
                usable = stream.read() and 1 == 0
                address = stream.read().toByte()
                stream.readNBytes(2)
                delay = stream.readShortLE().toInt()
            }
    }

    // 解析标签原始距离数据
    fun parseDistances(data: ByteArray) {
        distanceCnt++
        distances.clear()
        ByteArrayInputStream(data).use { stream ->
            repeat(3) {
                stream.read()
                distances += stream.read().toByte() to stream.readIntLE()
            }
        }
    }

    // 解析定位质量数据
    fun parseQuality(data: ByteArray) {
        qualityCnt++
        quality = data[1].toIntUnsigned()
    }

    // 打印
    override fun toString(): String {
        return "hedge = ${address}, x = $x, y = $y, z = $z, usable = $usable, delay = $delay, quality = $quality, Cnt = $positionCnt"
    }
}

// 标签设备
internal class Device(
    val id: Byte
) {
    var voltage = -1.0
    val cmd = Command(id, 0x03.toByte(), 0x0003.toShort(), 0x0002.toShort())
    // 解析
    fun parse(data: ByteArray) {
        // voltage = (data.copyOfRange(7, 9).toInt() and 0x1FFF) / 1000.0 // TODO 协议为0x0FFF但最大只能表示到4.096V
    }

    // 设置默认
    fun setDefault() {
        voltage = -1.0
    }

    // 打印
    override fun toString(): String {
        return "beacon${id}: ${voltage}V"
    }
}

// 地图
internal class Map(
    pathName: String
) {
    var filled = false
    val submaps = mutableListOf<ByteArray>()
    val beacons = mutableListOf<Pair<Byte, ByteArray>>()

    init {
        File(pathName)
            .takeIf(File::exists)
            ?.readBytes()
            ?.takeIf { it.isNotEmpty() && it.size % 81 == 0 }
            ?.let {
                for (i in it.indices step 81) {
                    if (it[i] == 0.toByte())
                        submaps.add(it.copyOfRange(i + 1, i + 81))
                    else if (it[i] == 1.toByte())
                        for (j in 1..80 step 14) {
                            if (it[i + j].toIntUnsigned() > 0)
                                beacons.add(Pair(it[i + j], it.copyOfRange(i + j + 1, i + j + 13)))
                            else
                                break
                        }
                }
                if (submaps.size > 0 && beacons.size > 0)
                    filled = true
            }
    }
}

internal sealed class DataPackage {
    object Nothing : DataPackage()
    object Failed : DataPackage()
    data class Data(val code: Byte, val payload: ByteArray) : DataPackage()
}

internal class Parse(val address: Byte, val packetType: Byte) {
    private val headLen: Int = when (packetType) {
        0x03.toByte() -> 3
        0x47.toByte() -> 5
        else          -> 3
    }

    /** MarvelMind 网络层解析器 */
    fun engine(): ParseEngine<Byte, DataPackage> =
        ParseEngine { buffer ->
            val size = buffer.size
            // 找到一个帧头
            var begin = (0 until size - 1).find { i ->
                buffer[i] == address && buffer[i + 1] == packetType
            } ?: return@ParseEngine ParseInfo(
                    nextHead = (if (buffer.last() == address) size - 1 else size),
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
                    DataPackage.Data(`package`[2], `package`.subList(headLen, `package`.size - 2).toByteArray())
                } else {
                    begin += 2
                    DataPackage.Failed
                }
            // 找到下一个帧头
            return@ParseEngine ParseInfo(begin, begin, result)
        }
}

