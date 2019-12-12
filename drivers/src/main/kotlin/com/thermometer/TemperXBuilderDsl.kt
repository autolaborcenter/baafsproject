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
class TemperXBuilderDsl private constructor() {
    // 指定串口名字
    var port: String? = null
    // 数据接收参数
    var retryInterval: Long = 100L
    var dataTimeout: Long = 2000L
    // 读温度周期1s
    var mainInterval: Long = 1000L
    // 调试参数
    var logger: SimpleLogger? = SimpleLogger("TemperX")

    companion object {
        /**
         * 在协程作用域上启动温度计
         * @param thermometer 输出温湿度
         * @param exceptions 输出异常信息
         * @param block 配置参数
         */
        fun CoroutineScope.startTemperX(
            thermometer: SendChannel<Stamped<Pair<Double, Double>>>,
            exceptions: SendChannel<ExceptionMessage>,
            block: TemperXBuilderDsl.() -> Unit = {}
        ) {
            TemperXBuilderDsl()
                .apply(block)
                .apply {
                    require(retryInterval > 0)
                    require(dataTimeout > 0)
                }
                .run {
                    TemperX(scope = this@startTemperX,
                            thermometer = thermometer,
                            exceptions = exceptions,
                            portName = port,
                            dataTimeout = dataTimeout,
                            retryInterval = retryInterval,
                            mainInterval = mainInterval,
                            logger = logger)
                }
        }
    }
}

