package com.marvelmind

import cn.autolabor.serialport.parser.ParseEngine
import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mechdancer.SimpleLogger
import org.mechdancer.WatchDog
import org.mechdancer.algebra.function.vector.div
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.common.Stamped
import org.mechdancer.exceptions.DataTimeoutException
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.exceptions.ExceptionMessage.Occurred
import org.mechdancer.exceptions.ExceptionMessage.Recovered
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Marvelmind定位系统驱动（移动标签与路由/读写地图/读写温度/读定位相关/读原始距离/数据记录/读电压等状态）
 */
internal class Marvelmind(
    scope: CoroutineScope,
    private val beaconOnMap: SendChannel<Stamped<Vector2D>>,
    private val thermometer: ReceiveChannel<Stamped<Pair<Double, Double>>>,
    private val exceptions: SendChannel<ExceptionMessage>,
    hedgePortName: String,  // 移动标签串口号
    modemPortName: String,  // 定位路由串口号
    tempInterval: Long,     // 设置温度周期
    locationTimeout: Long,  // 读定位超时时间
    stateInterval: Long,    // 读标签状态周期(电压)
    dataTimeout: Long,      // 数据超时时间
    private val retryInterval: Long,    // 串口重试周期
    delayLimit: Long,                   // 定位延时上限
    heightRange: ClosedFloatingPointRange<Double>,  // z值允许的范围
    private val hedgeIdList: ByteArray,             // 移动标签id列表
    private var beaconIdList: ByteArray,            // 固定标签列表
    private val logger: SimpleLogger?,              // 运行日志
    needModem: Boolean,     // 路由是否必需
    needMap: Boolean        // 地图是否必需
) : CoroutineScope by scope {
    // 协议解析引擎
    private val engineModem = Parse(0xFF.toByte(), 0x03.toByte()).engine()
    private val engineHedge = Parse(0xFF.toByte(), 0x47.toByte()).engine()
    private var engines = HashMap<Byte, ParseEngine<Byte, DataPackage>>()
    // 定位位置
    private var position = Position()
    // 温度
    private var temperature = DEFAULT_VAL
    // 湿度
    private var humidity = DEFAULT_VAL
    // 路由设置温度
    private var tempModem = DEFAULT_VAL.toInt()
    // 设备状态列表
    private var devices = HashMap<Byte, Device>()
    // 设备状态日志
    private val deviceLogger = SimpleLogger("device_state_log").apply { period = 1 }
    // 固定标签列表
//    private var beaconIdList = ByteArray(0)
    // 地图
    private var map = Map("marvelmind.map")
    // 数据记录
    private var dataLoggers = emptyArray<SimpleLogger>()
    // 原始距离
    private var rawDistances = IntArray(0)
    // 超时异常监控
    private val dataTimeoutException =
        DataTimeoutException(NAME, dataTimeout)
    private val dataWatchDog = WatchDog(timeout = dataTimeout) { exceptions.send(Occurred(dataTimeoutException)) }
    // 数据过滤
    private val delayRange = 1..delayLimit
    private val zRange = (heightRange.start * 1000).roundToInt()..(heightRange.endInclusive * 1000).roundToInt()

    // 常量参数
    private companion object {
        const val NAME = "marvelmind"
        const val BUFFER_SIZE = 1024
        const val DEFAULT_VAL = -300.0
        const val WRITE_RECON_CNT = 1 // 写重连阈值(超过此次数写失败则串口重连)
        const val WRITE_DELAY = 100L
        val CMD_CONFIG = Command(0xFF.toByte(), 0x03.toByte(), 0x5000.toShort(), 0x0000.toShort())
        val CMD_RAW_DIS = Command(0xFF.toByte(), 0x03.toByte(), 0x4000.toShort(), 0x0000.toShort())
    }

    init {
        // 单开线程以执行阻塞读写
        launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) {
            val buffer = ByteArray(BUFFER_SIZE)
            // 串口初始化
            val hedgePort = createPort(hedgePortName)
            var modemPort: SerialPort? = null
            if (needModem)
                modemPort = createPort(modemPortName)
            // 检查地图
            if (needMap && needModem) {
                while (!map.filled) {
                    log(logger, "reload mervelmind.map after 5s")
                    delay(5000)
                    map = Map("marvelmind.map")
                }
                beaconIdList = ByteArray(map.beacons.size) { map.beacons[it].first }
                // 检查submap
                for (i in map.submaps.indices) {
                    request(
                        modemPort!!,
                        Command(0xFF.toByte(), 0x03.toByte(), (0x6000 + i).toShort(), 0x0000.toShort()),
                        buffer
                    )
                    //modemPort.writeOrReboot(CommandSubmap(i.toByte(), map.submaps[i]).data)
                    log(logger, "check submap${i}")
                    delay(WRITE_DELAY)
                }
                // 写固定标签坐标
                for (item in map.beacons) {
                    modemPort!!.writeOrReboot(CommandCoordinate(item.first, item.second).data)
                    log(logger, "write beacon${item.first} coordinate")
                    delay(WRITE_DELAY)
                }
            }
            // 初始化和固定标签相关的量
            for(id in beaconIdList) {
                devices[id] = Device(id)
                engines[id] = Parse(id, 0x03.toByte()).engine()
            }
            // 初始化和移动标签数相关的量
            rawDistances = IntArray(beaconIdList.size) { -1 }
            dataLoggers = Array(hedgeIdList.size) { SimpleLogger("data_log_${hedgeIdList[it]}") }
            for(id in hedgeIdList) {
                devices[id] = Device(id)
                engines[id] = Parse(id, 0x03.toByte()).engine()
            }

            var tempClock = 0L
            var stateClock = 0L
            val beaconNum = devices.size
            var stateCnt = beaconNum
            val idList = beaconIdList + hedgeIdList
            while (true) {
                // 设置温度
                if (needModem) {
                    if (temperature > DEFAULT_VAL && (System.currentTimeMillis() - tempClock) >= tempInterval) {
                        tempClock = System.currentTimeMillis()
                        request(modemPort!!, CMD_CONFIG, buffer)
                    }
                }
                // 读取定位/质量/原始距离
                position.setDefault()
                val clockLocation = System.currentTimeMillis()
                while ((System.currentTimeMillis() - clockLocation) <= locationTimeout) {
                    val len = hedgePort.readBytes(buffer, BUFFER_SIZE.toLong())
                    if (len > 0) {
                        parseHedge(buffer.take(len))
                        if ((position.positionCnt > 0 && /*position.distanceCnt > 0 &&*/ position.qualityCnt > 0) || position.positionCnt > 1)
                            break
                    } else if (len < 0) {
                        hedgePort.writeBytes(buffer, 0)
                        if (!hedgePort.isOpen)
                            hedgePort.openPort()
                    }
                }
                // 读取原始距离
                if (needModem) {
                    if (position.positionCnt > 0)
                        request(modemPort!!, CMD_RAW_DIS, buffer)
                }
                // 数据记录
                if (position.positionCnt > 0) {
                    if (hedgeIdList.contains(position.address)) {
                        val logIndex = hedgeIdList.indexOf(position.address)
                        val rec = StringBuilder(
                                "${tempModem}\t${temperature}\t${humidity}\t${position.x}\t${position.y}\t${position.z}\t${position.quality}\t")
                        for (item in position.distances) {
                            rec.append("${item.first}\t${item.second}\t")
                        }
                        for (item in rawDistances) {
                            rec.append("${item}\t")
                        }
                        log(dataLoggers[logIndex], rec.toString(), LogType.WritePrint)
                    } else
                        log(logger, "unknown hedgehog ${position.address}", LogType.WritePrint)
                }
                // 读取标签状态(电压/sleep)
                if (needModem) {
                    if (stateCnt < beaconNum || (System.currentTimeMillis() - stateClock) >= stateInterval) {
                        if (stateCnt >= beaconNum) {
                            stateCnt = 0
                            stateClock = System.currentTimeMillis()
                            for ((_, dev) in devices)
                                dev.setDefault()
                        }
                        request(modemPort!!, devices[idList[stateCnt]]!!.cmd, buffer)
                        if (devices[idList[stateCnt]]!!.voltage > -1)
                            stateCnt++
                    }
                    if (stateCnt == beaconNum) {
                        val text = StringBuilder()
                        for ((_, dev) in devices) {
                            text.append(dev).append(", ")
                        }
                        text.trim { ch -> ch == ' ' || ch == ',' }
                        log(deviceLogger, text.toString(), LogType.WritePrint)
                        stateCnt++
                        // 唤醒标签
                        for ((_, dev) in devices) {
                            if (dev.sleep) {
                                modemPort!!.writeOrReboot(CommandWake(dev.id).data)
                                log(logger, "wake beacon${dev.id}")
                                delay(WRITE_DELAY)
                            }
                        }
                    }
                }
            }
        }.invokeOnCompletion {
            beaconOnMap.close()
        }
        // 接收温湿度
        launch {
            for ((stamp, p) in thermometer) {
                temperature = p.first
                humidity = p.second
                //println("${temperature},${humidity}")
            }
        }
    }

    // 请求数据（先写指令再读返回数据）
    private suspend fun request(port: SerialPort, cmd: Command, buffer: ByteArray) {
        port.writeOrReboot(cmd.data)
        while (true) {
            val len = port.readBytes(buffer, BUFFER_SIZE.toLong())
            if (len > 0)
                parseModem(port, cmd, buffer.take(len))
            else
                break
        }
    }

    // 判重
    private val memory = Array(hedgeIdList.size) { Position(hedgeIdList[it]) }

    private fun notStatic(pos: Position): Boolean {
        val index = hedgeIdList.indexOf(pos.address)
        val equal = pos.x == memory[index].x && pos.y == memory[index].y && pos.z == memory[index].z
        memory[index].x = pos.x
        memory[index].y = pos.y
        memory[index].z = pos.z
        return !equal
    }

    // 设置温度指令
    private fun makeTempCommand(data: ByteArray, temp: Double): ByteArray? {
        tempModem = temp.roundToInt()
        val temp_data = tempModem - 23
        log(logger, "real temp = ${temp}, modem temp = ${data[20].toInt() + 23}", LogType.WritePrint)
        if (temp_data == data[20].toInt()) {
            return null
        }
        val list = arrayListOf<Byte>()
        list.addAll(CMD_CONFIG.data.take(6))
        list[1] = 0x10.toByte()
        list.add(0x30.toByte())
        data[20] = temp_data.toByte()
        list.addAll(data.toList())
        list.addAll(crc16(list.toByteArray()).toList())
        return list.toByteArray()
    }

    // 设置submap指令
    private fun makeSubmapCommand(data: ByteArray, address: Byte): ByteArray? {
        val index = address.toIntUnsigned()
        if (index < map.submaps.size && data.size == map.submaps[index].size) {
            var equal = true
            for (i in data.indices) {
                if (data[i] != map.submaps[index][i]) {
                    equal = false
                    break
                }
            }
            if (!equal)
                return CommandSubmap(address, map.submaps[index]).data
        }
        return null
    }

    // 解析路由原始距离数据
    private fun resolveRawDistances(data: ByteArray) {
        for (i in rawDistances.indices) {
            rawDistances[i] = -1
        }
        for (i in 0..7) {
            val idRecv = data[4 * i]
            val idSend = data[4 * i + 1]
            if (beaconIdList.contains(idRecv) && idSend == position.address)
                rawDistances[beaconIdList.indexOf(idRecv)] = shortLEOfU(data[4 * i + 2], data[4 * i + 3]).toInt()
            else if (idRecv == 0.toByte() && idSend == 0.toByte())
                break
        }
    }

    // 处理路由串口数据
    private fun parseModem(port: SerialPort, cmd: Command, array: List<Byte>) {
        var engine = engineModem
        if (cmd.address != 0xFF.toByte())
            engine = engines[cmd.address]!!
        engine(array) { pack ->
            when (pack) {
                is DataPackage.Nothing -> logger?.log("nothing")
                is DataPackage.Failed  -> logger?.log("failed")
                is DataPackage.Data    -> {
                    // 不同类型数据处理
                    if (cmd.code == 0x5000.toShort())           // 路由配置
                        makeTempCommand(pack.payload, temperature)
                            ?.let { launch { port.writeOrReboot(it) } } // 写温度
                    else if (cmd.code == 0x4000.toShort())      // 原始距离
                        resolveRawDistances(pack.payload)
                    else if (cmd.code == 0x0003.toShort()) {    // device
                        if (beaconIdList.contains(cmd.address) || hedgeIdList.contains(cmd.address))
                            devices[cmd.address]!!.parse(pack.payload)
                    }
                    else if ((cmd.code.toInt() shr 8) == 0x60)  // submap
                        makeSubmapCommand(pack.payload, (cmd.code.toInt() and 0xFF).toByte())
                            ?.let { launch { port.writeOrReboot(it) } }
                }
            }
        }
    }

    // 处理移动标签串口数据
    private fun parseHedge(array: List<Byte>) {
        engineHedge(array) { pack ->
            when (pack) {
                is DataPackage.Nothing -> logger?.log("nothing")
                is DataPackage.Failed  -> logger?.log("failed")
                is DataPackage.Data    -> {
                    when (pack.code) {
                        0x11.toByte() -> position.parsePosition(pack.payload)
                        0x04.toByte() -> position.parseDistances(pack.payload)
                        0x07.toByte() -> position.parseQuality(pack.payload)
                    }
                    if (position.positionCnt > 0 && position.qualityCnt > 0)
                        log(logger, position.toString(), LogType.WritePrint)
                    // 发送定位
                    if (position.positionCnt > 0
                        && hedgeIdList.contains(position.address)
                        && position.usable
                        && position.qualityCnt > 0
                        && position.delay in delayRange
                        && position.z in zRange
                        && notStatic(position)
                    ) { // TODO 定位质量 多移动标签
                        launch {
                            beaconOnMap.send(Stamped(
                                    System.currentTimeMillis() - position.delay,
                                    vector2DOf(position.x, position.y) / 1000.0))
                            dataWatchDog.feed()
                            launch { exceptions.send(Recovered(dataTimeoutException)) }
                        }
                    }
                }
            }
        }
    }

    // 创建串口
    private suspend fun createPort(portName: String): SerialPort {
        while (true) {
            try {
                val port = SerialPort.getCommPort(portName)
                port.baudRate = 115200
                port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 100)
                return port
            } catch (e: Exception) {
            }
            log(logger, "failed to create serial port, retry after ${retryInterval / 1000}s", LogType.WritePrint)
            // 等待一段时间重试
            delay(retryInterval)
        }
    }

    // 写计数
    private var writeFailCnt = 0

    // 写串口（失败重连机制）
    private suspend fun SerialPort.writeOrReboot(buffer: ByteArray) {
        //println(buffer.joinToString(" "){Integer.toHexString(it.toInt()).takeLast(2)})
        while (true) {
            // 打开串口并写指令
            if (takeIf { this.isOpen || this.openPort() }?.writeBytes(buffer, buffer.size.toLong()) == buffer.size) {
                writeFailCnt = 0
                return
            } else {
                writeFailCnt++
                if (writeFailCnt >= WRITE_RECON_CNT)
                    log(logger, "failed to open or write, retry after ${retryInterval / 1000}s", LogType.WritePrint)
            }
            // 等待一段时间重试
            delay(retryInterval)
        }
    }

    // 日志类型
    enum class LogType {
        WriteOnly,
        PrintOnly,
        WritePrint
    }

    // 日志
    private fun log(logger: SimpleLogger?, text: String, type: LogType = LogType.WritePrint) {
        if (type == LogType.WriteOnly || type == LogType.WritePrint) {
            logger?.log(text)
        }
        if (type == LogType.PrintOnly || type == LogType.WritePrint) {
            println(text)
        }
    }
}

