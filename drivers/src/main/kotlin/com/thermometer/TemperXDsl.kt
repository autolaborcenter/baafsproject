package com.thermometer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import org.mechdancer.SimpleLogger
import org.mechdancer.annotations.BuilderDslMarker
import org.mechdancer.common.Stamped
import org.mechdancer.exceptions.ExceptionMessage

/**
 * 温度计模块构建器
 */
@BuilderDslMarker
class TemperXDsl private constructor() {
    // 指定串口名字
    var port: String? = null
    // 数据接收参数
    var retryInterval: Long = 100L
    var connectionTimeout: Long = 2000L
    var parseTimeout: Long = 2000L
    var dataTimeout: Long = 2000L
    // 读温度周期1s
    var mainInterval: Long = 1000L
    // 调试参数
    var logger: SimpleLogger? = SimpleLogger("TemperX")

    companion object {
        /**
         * 在协程作用域上启动温度计
         * @param therm 输出温湿度
         * @param exceptions 输出异常信息
         * @param block 配置参数
         */
        fun CoroutineScope.startTemperX(
            therm: SendChannel<Stamped<Pair<Double, Double>>>,
            exceptions: SendChannel<ExceptionMessage>,
            block: TemperXDsl.() -> Unit = {}
        ) {
            TemperXDsl()
                .apply(block)
                .apply {
                    require(retryInterval > 0)
                    require(connectionTimeout > 0)
                    require(parseTimeout > 0)
                    require(dataTimeout > 0)
                }
                .run {
                    TemperX(
                        scope = this@startTemperX,
                        therm = therm,
                        exceptions = exceptions,
                        portName = port,
                        connectionTimeout = connectionTimeout,
                        parseTimeout = parseTimeout,
                        dataTimeout = dataTimeout,
                        retryInterval = retryInterval,
                        mainInterval = mainInterval,
                        logger = logger
                    )
                }
        }
    }
}

