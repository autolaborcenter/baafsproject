package com.marvelmind

import cn.autolabor.pm1.model.ControlVariable
import com.marvelmind.MarvelmindBuilderDsl.Companion.startMarvelmind
import com.thermometer.TemperXBuilderDsl.Companion.startTemperX
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.runBlocking
import org.mechdancer.SimpleLogger
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.annotations.BuilderDslMarker
import org.mechdancer.channel
import org.mechdancer.common.Stamped
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.exceptions.ExceptionServerBuilderDsl.Companion.startExceptionServer
import org.mechdancer.geometry.angle.toRad

/**
 * Marvelmind模块构建器
 */
@BuilderDslMarker
class MarvelmindBuilderDsl private constructor() {
    var hedgePortName: String = "/dev/hedge"// 移动标签串口号
    var modemPortName: String = "/dev/modem"// 定位路由串口号
    var tempInterval: Long = 60*1000L       // 设置温度周期
    var locationTimeout: Long = 1000L       // 读定位超时时间
    var stateInterval: Long = 10*60*1000L   // 读标签状态周期(电压)
    var dataTimeout: Long = 2000L   // 数据超时时间
    var retryInterval: Long = 1000L // 串口重试周期
    var delayLimit: Long = 400L     // 定位延时上限
    var heightRange: ClosedFloatingPointRange<Double> =
        Double.NEGATIVE_INFINITY..Double.POSITIVE_INFINITY  // z值允许的范围
    var hedgeIdList: List<Byte> = arrayListOf(24)           // 移动标签id列表
    var logger: SimpleLogger? = SimpleLogger("Marvelmind")  // 运行日志
    var needModem: Boolean = true   // 路由是否必需
    var needMap: Boolean = true     // 地图是否必需
    companion object {
        /**
         * 在协程作用域上启动Marvelmind
         * @param location 输出定位
         * @param thermometer 输入温湿度
         * @param exceptions 输出异常信息
         * @param block 配置参数
         */
        fun CoroutineScope.startMarvelmind(
            location: SendChannel<Stamped<Vector2D>>,
            thermometer: ReceiveChannel<Stamped<Pair<Double, Double>>>,
            exceptions: SendChannel<ExceptionMessage>,
            block: MarvelmindBuilderDsl.() -> Unit = {}
        ) {
            MarvelmindBuilderDsl()
                .apply(block)
                .apply {
                    require(dataTimeout > 0)
                    require(delayLimit > 1)
                }
                .run {
                    Marvelmind(
                        scope = this@startMarvelmind,
                        beaconOnMap = location,
                        thermometer = thermometer,
                        exceptions = exceptions,
                        hedgePortName = hedgePortName,
                        modemPortName = modemPortName,
                        tempInterval = tempInterval,
                        locationTimeout = locationTimeout,
                        stateInterval = stateInterval,
                        dataTimeout = dataTimeout,
                        retryInterval = retryInterval,
                        delayLimit = delayLimit,
                        heightRange = heightRange,
                        hedgeIdList = hedgeIdList,
                        logger = logger,
                        needModem = needModem,
                        needMap = needMap
                    )
                }
        }
    }
}

fun main()= runBlocking(Dispatchers.Default) {
    // 话题
    val exceptions = channel<ExceptionMessage>()
    val beaconOnMap = channel<Stamped<Vector2D>>()
    val thermometer = channel<Stamped<Pair<Double, Double>>>()

    // 启动异常服务器
    val exceptionServer = startExceptionServer(exceptions) { }

    startTemperX(thermometer, exceptions){
        port = "COM28"
    }

    startMarvelmind(beaconOnMap, thermometer, exceptions) {
        hedgePortName = "COM25"
        modemPortName = "COM3"
        hedgeIdList = arrayListOf(16)
    }
}

