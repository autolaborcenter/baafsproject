package com.marvelmind.mobilebeacon

import cn.autolabor.serialport.manager.SerialPortManager
import kotlinx.coroutines.channels.SendChannel
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.annotations.BuilderDslMarker
import org.mechdancer.common.Stamped
import org.mechdancer.exceptions.ExceptionMessage

/**
 * 定位标签模块构建器
 */
@BuilderDslMarker
class SerialPortMobileBeaconBuilderDsl private constructor() {
    // 指定串口名字
    var portName: String? = null

    // 数据接收参数
    var dataTimeout: Long = 2000L

    // 数据过滤参数
    var delayLimit: Long = 400L
    var heightRange: ClosedFloatingPointRange<Double> =
        Double.NEGATIVE_INFINITY..Double.POSITIVE_INFINITY

    companion object {
        /**
         * @param beaconOnMap 输出标签在地图上的位置
         * @param exceptions 输出异常信息
         * @param block 配置参数
         */
        fun SerialPortManager.registerMobileBeacon(
            beaconOnMap: SendChannel<Stamped<Vector2D>>,
            beaconData: SendChannel<Stamped<MobileBeaconData>>,
            exceptions: SendChannel<ExceptionMessage>,
            block: SerialPortMobileBeaconBuilderDsl.() -> Unit = {}
        ) = SerialPortMobileBeaconBuilderDsl()
            .apply(block)
            .apply {
                require(dataTimeout > 0)
                require(delayLimit > 1)
            }
            .run {
                SerialPortMobilBeacon(
                    beaconOnMap = beaconOnMap,
                    beaconData = beaconData,
                    exceptions = exceptions,
                    portName = portName,
                    dataTimeout = dataTimeout,
                    delayLimit = delayLimit,
                    heightRange = heightRange
                )
            }
            .also(this::register)
    }
}
