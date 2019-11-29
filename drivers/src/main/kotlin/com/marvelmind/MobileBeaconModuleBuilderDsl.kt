package com.marvelmind

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import org.mechdancer.SimpleLogger
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.annotations.BuilderDslMarker
import org.mechdancer.common.Stamped
import org.mechdancer.exceptions.ExceptionMessage

/**
 * 定位标签模块构建器
 */
@BuilderDslMarker
class MobileBeaconModuleBuilderDsl private constructor() {
    // 指定串口名字
    var port: String? = null
    // 数据接收参数
    var retryInterval: Long = 100L
    var connectionTimeout: Long = 2000L
    var parseTimeout: Long = 2000L
    var dataTimeout: Long = 2000L
    // 数据过滤参数
    var delayLimit: Long = 400L
    var heightRange: ClosedFloatingPointRange<Double> =
        Double.NEGATIVE_INFINITY..Double.POSITIVE_INFINITY
    // 调试参数
    var logger: SimpleLogger? = SimpleLogger("MarvelmindMobileBeacon")

    companion object {
        /**
         * 在协程作用域上启动定位标签
         * @param beaconOnMap 输出标签在地图上的位置
         * @param exceptions 输出异常信息
         * @param block 配置参数
         */
        fun CoroutineScope.startMobileBeacon(
            beaconOnMap: SendChannel<Stamped<Vector2D>>,
            exceptions: SendChannel<ExceptionMessage>,
            block: MobileBeaconModuleBuilderDsl.() -> Unit = {}
        ) {
            MobileBeaconModuleBuilderDsl()
                .apply(block)
                .apply {
                    require(retryInterval > 0)
                    require(connectionTimeout > 0)
                    require(parseTimeout > 0)
                    require(dataTimeout > 0)
                    require(delayLimit > 1)
                }
                .run {
                    MarvelmindMobilBeacon(
                            scope = this@startMobileBeacon,
                            beaconOnMap = beaconOnMap,
                            exceptions = exceptions,
                            portName = port,
                            connectionTimeout = connectionTimeout,
                            parseTimeout = parseTimeout,
                            dataTimeout = dataTimeout,
                            retryInterval = retryInterval,
                            delayLimit = delayLimit,
                            heightRange = heightRange,
                            logger = logger)
                }
        }
    }


}
