package com.marvelmind.modem

import cn.autolabor.serialport.manager.SerialPortManager
import com.marvelmind.mobilebeacon.MobileBeaconData
import com.thermometer.Humiture
import kotlinx.coroutines.channels.ReceiveChannel
import org.mechdancer.SimpleLogger
import org.mechdancer.annotations.BuilderDslMarker
import org.mechdancer.common.Stamped

/**
 * 路由模块构建器
 */
@BuilderDslMarker
class SerialPortModemBuilderDsl
private constructor() {
    // 指定串口名字
    var portName: String? = null

    // 工作周期
    var tempInterval: Long = 60 * 1000L
    var stateInterval: Long = 10 * 60 * 1000L

    // 移动标签id列表
    var hedgeIdList: ByteArray = ByteArray(0)

    // 运行日志
    var logger: SimpleLogger? = SimpleLogger("MarvelmindModem")

    companion object {
        /**
         * @param humitures 输入温湿度数据
         * @param hedgehog 输入定位数据
         * @param block 配置参数
         */
        fun SerialPortManager.registerModem(
            humitures: ReceiveChannel<Stamped<Humiture>>,
            hedgehog: ReceiveChannel<Stamped<MobileBeaconData>>,
            block: SerialPortModemBuilderDsl.() -> Unit = {}
        ) = SerialPortModemBuilderDsl()
            .apply(block)
            .apply {
                require(tempInterval > 0)
                require(stateInterval > 0)
                require(hedgeIdList.isNotEmpty())
            }
            .run {
                SerialPortModem(
                    humitures = humitures,
                    hedgehog = hedgehog,
                    portName = portName,
                    tempInterval = tempInterval,
                    stateInterval = stateInterval,
                    hedgeIdList = hedgeIdList,
                    logger = logger
                )
            }
            .also(this::register)
    }
}
