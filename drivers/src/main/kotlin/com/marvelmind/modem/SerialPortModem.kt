package com.marvelmind.modem

import cn.autolabor.serialport.manager.Certificator
import cn.autolabor.serialport.manager.SerialPortDeviceBase
import com.marvelmind.dataEquals
import com.marvelmind.mobilebeacon.MobileBeaconData
import com.marvelmind.shortLEOfU
import com.marvelmind.toIntUnsigned
import com.thermometer.Humiture
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.mechdancer.SimpleLogger
import org.mechdancer.WatchDog
import org.mechdancer.common.Stamped
import org.mechdancer.exceptions.DataTimeoutException
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.exceptions.ExceptionMessage.Occurred
import org.mechdancer.exceptions.ExceptionMessage.Recovered
import java.util.concurrent.PriorityBlockingQueue
import kotlin.math.roundToInt

/**
 * Marvelmind路由驱动（读写地图/设置温度/读原始距离/数据记录/读电压等状态）
 */
class SerialPortModem
internal constructor(
    private val humitures: ReceiveChannel<Stamped<Humiture>>,
    private val hedgehog: ReceiveChannel<Stamped<MobileBeaconData>>,
    private val exceptions: SendChannel<ExceptionMessage>,

    portName: String?,  // 定位路由串口号

    private val tempInterval: Long,     // 设置温度周期
    private val stateInterval: Long,    // 读标签状态周期(电压)
    dataTimeout: Long,                  // 数据超时时间

    private val hedgeIdList: ByteArray, // 移动标签id列表
    private val logger: SimpleLogger?   // 运行日志
) : SerialPortDeviceBase(NAME, 115200, 1024, portName) {
    // 协议解析引擎
    private var engine = engine()
    // 设备状态列表
    private var devices = emptyArray<Device>()
    // 所有标签id列表
    private var idList = ByteArray(0)
    // 固定标签id列表
    private var beaconIdList = ByteArray(0)
    // 设备状态日志
    private val deviceLogger = SimpleLogger("device_state_log").apply { period = 1 }
    // 地图
    private var map = Map("marvelmind.map")
    // 数据记录
    private var dataLoggers = emptyArray<SimpleLogger>()
    // 原始距离
    private var rawDistances = IntArray(0)
    // 温湿度
    private var humiture = Humiture(DEFAULT_VAL, DEFAULT_VAL)
    // 路由设置温度
    private var tempModem = DEFAULT_VAL.toInt()
    // 上一个定位数据字符串
    private var lastLocation = emptyArray<Stamped<MobileBeaconData>?>()
    // 认证串口时收到的beacon坐标数据
    private var beaconData = ByteArray(0)
    // submap编号(由于submap返回数据中没有编号，则在发送请求时记录编号)
    private var submapNumber = -1
    // device序号(用于对应收发)
    private var deviceIndex = -1
    // 数据请求队列
    private val requestQueue = PriorityBlockingQueue<Command>()
    // 超时异常监控
    private val dataTimeoutException = DataTimeoutException(NAME, dataTimeout)
    private val dataWatchDog = WatchDog(timeout = dataTimeout) { exceptions.send(Occurred(dataTimeoutException)) }

    override fun buildCertificator(): Certificator =
        object : CertificatorBase(CERT_TIMEOUT) {
            override val activeBytes = byteArrayOf()
            override fun invoke(bytes: Iterable<Byte>): Boolean? {
                var result = false
                engine(bytes) { pack ->
                    when (pack) {
                        is DataPackage.Nothing -> logger?.log("nothing")
                        is DataPackage.Failed  -> logger?.log("failed")
                        is DataPackage.Data    -> {
                            parse(pack)
                            result = beaconData.isNotEmpty()
                        }
                    }
                }
                return passOrTimeout(result)
            }
        }

    override fun setup(
        scope: CoroutineScope,
        toDevice: SendChannel<List<Byte>>,
        fromDevice: ReceiveChannel<List<Byte>>
    ) {
        // 定时请求配置(温度)
        val jobConfig = scope.launch(start = CoroutineStart.LAZY) {
            while (humiture.temperature < DEFAULT_VAL + 1)
                delay(1000L)
            while (isActive) {
                requestQueue.offer(CMD_CONFIG)
                delay(tempInterval)
            }
        }
        // 定时请求状态(电压)
        val jobState = scope.launch(start = CoroutineStart.LAZY) {
            while (isActive) {
                devices.forEach { requestQueue.offer(it.cmd) }
                delay(stateInterval)
            }
        }
        // 接收定位数据、请求原始距离(温度)
        val jobRaw = scope.launch(start = CoroutineStart.LAZY) {
            for ((stamp, data) in hedgehog) {
                // 记录上一次定位
                if (hedgeIdList.contains(data.address)) {
                    val index = hedgeIdList.indexOf(data.address)
                    lastLocation[index]?.let {
                        val record = buildString {
                            val (t, d) = it
                            val (_, x, y, z, available, _, rawDistance) = d
                            append("${t}\t")
                            append("${tempModem}\t")
                            append("${humiture.temperature}\t")
                            append("${humiture.humidity}\t")
                            append("${x}\t")
                            append("${y}\t")
                            append("${z}\t")
                            append("${if (available) 1 else 0}\t")
                            append("(quality ?: -1)\t")
                            rawDistance?.forEach { address, value ->
                                append("${address.toIntUnsigned()}\t${value}\t")
                            } ?: run {
                                append("-1\t-1\t-1\t-1\t")
                            }
                            for (distance in rawDistances)
                                append("${distance}\t")
                            for (i in rawDistances.indices)
                                rawDistances[i] = -1
                        }
                        log(dataLoggers[index], record)
                        lastLocation[index] = Stamped(stamp, data)
                    }
                    requestQueue.offer(CMD_RAW_DIS)
                } else
                    log(logger, "unknown hedgehog ${data.address.toIntUnsigned()}")
                if (!isActive)
                    break
            }
        }
        // 处理请求队列
        scope.launch {
            while (isActive) {
                requestQueue.poll().let {
                    if (it !is Command.CommandRawDistanceR
                        || requestQueue.element() !is Command.CommandRawDistanceR
                    ) {
                        when (it) {
                            is Command.CommandSubmapR -> submapNumber = it.address.toIntUnsigned()
                            is Command.CommandStateR  -> deviceIndex = idList.indexOf(it.address)
                        }
                        toDevice.send(it.data.asList())
                    }
                }
                delay(REQUEST_INTERVAL)
            }
        }
        // 接收串口数据
        scope.launch {
            for (bytes in fromDevice)
                engine(bytes) { pack ->
                    when (pack) {
                        is DataPackage.Nothing -> logger?.log("nothing")
                        is DataPackage.Failed  -> logger?.log("failed")
                        is DataPackage.Data    ->
                            launch {
                                assert(parse(pack).all(requestQueue::offer))
                                exceptions.send(Recovered(dataTimeoutException))
                                dataWatchDog.feed()
                            }
                    }
                }
        }
        // 接收温湿度
        scope.launch {
            for ((_, data) in humitures)
                humiture = data
        }
        // 初始化
        scope.launch {
            // 确保读到地图
            while (!map.filled) {
                log(logger, "reload marvelmind.map after 5s")
                delay(5000)
                map = Map("marvelmind.map")
            }
            // 检查固定标签坐标
            checkBeaconCoordinate(beaconData)  // TODO beaconData要不要判空
                .takeIf { it.isNotEmpty() }
                ?.forEach { requestQueue.offer(it) }
            // 检查 submap
            for (i in map.submaps.indices)
                requestQueue.offer(Command.CommandSubmapR(i.toByte()))
            // 初始化和标签数相关的量
            beaconIdList = ByteArray(map.beacons.size) { map.beacons[it].first }
            rawDistances = IntArray(beaconIdList.size) { -1 }
            dataLoggers = Array(hedgeIdList.size) { SimpleLogger("data_log_${hedgeIdList[it]}") }
            lastLocation = Array(hedgeIdList.size) { null }
            idList = beaconIdList + hedgeIdList
            devices = Array(idList.size) { Device(idList[it]) }
            // 启动定时请求
            jobConfig.start()
            jobState.start()
            jobRaw.start()
        }
    }

    // 数据解析
    private fun parse(
        pack: DataPackage.Data
    ): List<Command> {
        val cmdList = arrayListOf<Command>()
        val (type, payload) = pack
        when (type) {
            0x12 -> {   // 固定标签坐标
                if (beaconData.isEmpty())
                    beaconData = payload.clone()
            }
            0x50 -> {   // submap
                if (submapNumber >= 0) {
                    checkSubmap(submapNumber.toByte(), payload)
                        ?.let { cmdList.add(it) } // 写submap
                    submapNumber = -1
                }
            }
            0x20 -> {   // 标签状态(电压)
                if (deviceIndex >= 0) {
                    val index = deviceIndex
                    deviceIndex = -1
                    devices[index].parse(payload)
                    if (devices[index].sleep) {
                        // 唤醒标签
                        cmdList.add(Command.CommandWakeW(devices[index].id))
                        log(logger, "wake beacon${devices[index].id.toIntUnsigned()}")
                    }
                    // 记录标签电压
                    log(deviceLogger, devices[index].toString())
//                    // 前面获取失败的标签重试
//                    for (i in 0 until index) {
//                        if (devices[i].voltage == -1)
//                            cmdList.add(devices[i].cmd)
//                    }
                }
            }
            0x30 -> {   // 标签配置(温度)
                checkTemperature(payload, humiture.temperature)
                    ?.let { cmdList.add(it) } // 写配置(温度)
            }
            0x28 -> {   // 原始距离
                resolveRawDistances(payload)
            }
        }
        return cmdList
    }

    // 常量参数
    private companion object {
        const val NAME = "marvelmind modem"
        const val DEFAULT_VAL = -300.0
        const val CERT_TIMEOUT = 11000L
        const val REQUEST_INTERVAL = 50L
        val CMD_CONFIG = Command.CommandConfigR(0xFF.toByte())
        val CMD_RAW_DIS = Command.CommandRawDistanceR(0xFF.toByte())
    }

    private fun contains(list: Array<Pair<Byte, ByteArray>>, item: Pair<Byte, ByteArray>): Boolean {
        for ((a, b) in list) {
            if (item.first == a && item.second.dataEquals(b))
                return true
        }
        return false
    }

    // 检查固定标签坐标
    private fun checkBeaconCoordinate(data: ByteArray): List<Command> {
        val cmdList = arrayListOf<Command>()
        val beacons = Array(data[0].toIntUnsigned()) {
            Pair(data[14 * it + 1], data.copyOfRange(14 * it + 2, 14 * it + 14))
        }
        for (item in map.beacons) {
            if (!contains(beacons, item)) {
                cmdList.add(Command.CommandCoordinateW(item.first, item.second))
            }
        }
        return cmdList
    }

    // 检查submap
    private fun checkSubmap(address: Byte, data: ByteArray): Command? {
        val index = address.toIntUnsigned()
        if (index < map.submaps.size && !data.dataEquals(map.submaps[index]))
            return Command.CommandSubmapW(address, map.submaps[index])
        return null
    }

    // 检查温度
    private fun checkTemperature(data: ByteArray, temp: Double): Command? {
        tempModem = temp.roundToInt()
        val tempData = tempModem - 23
        log(logger, "real temp = ${temp}, modem temp = ${data[20].toInt() + 23}")
        if (tempData == data[20].toInt()) {
            return null
        }
        data[20] = tempData.toByte()
        return Command.CommandConfigW(0xFF.toByte(), data)
    }

    // 处理原始距离数据
    private fun resolveRawDistances(data: ByteArray) {
        for (i in 0..7) {
            val idReceive = data[4 * i]
            val idSend = data[4 * i + 1]
            if (beaconIdList.contains(idReceive) && hedgeIdList.contains(idSend))
                rawDistances[beaconIdList.indexOf(idReceive)] =
                    shortLEOfU(data[4 * i + 2], data[4 * i + 3])
            else if (idReceive == 0.toByte() && idSend == 0.toByte())
                break
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
        if (type == LogType.WriteOnly || type == LogType.WritePrint)
            logger?.log(text)
        if (type == LogType.PrintOnly || type == LogType.WritePrint)
            println(text)
    }
}
