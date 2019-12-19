package com.marvelmind.modem

import cn.autolabor.serialport.manager.SerialPortManager
import com.marvelmind.mobilebeacon.MobileBeaconData
import com.thermometer.Humiture
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.mechdancer.SimpleLogger
import org.mechdancer.annotations.BuilderDslMarker
import org.mechdancer.common.Stamped
import org.mechdancer.exceptions.ExceptionMessage

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
    // 超时时间
    var dataTimeout: Long = 2000L
    // 移动标签id列表
    var hedgeIdList: ByteArray = ByteArray(0)
    // 运行日志
    var logger: SimpleLogger? = SimpleLogger("MarvelmindModem")

    companion object {
        /**
         * @param humitures 输入温湿度数据
         * @param hedgehog 输入定位数据
         * @param exceptions 输出异常信息
         * @param block 配置参数
         */
        fun SerialPortManager.registerModem(
            humitures: ReceiveChannel<Stamped<Humiture>>,
            hedgehog: ReceiveChannel<Stamped<MobileBeaconData>>,
            exceptions: SendChannel<ExceptionMessage>,
            block: SerialPortModemBuilderDsl.() -> Unit = {}
        ) = SerialPortModemBuilderDsl()
            .apply(block)
            .apply {
                require(tempInterval > 0)
                require(stateInterval > 0)
                require(dataTimeout > 0)
                require(hedgeIdList.isNotEmpty())
            }
            .run {
                SerialPortModem(
                        humitures = humitures,
                        hedgehog = hedgehog,
                        exceptions = exceptions,
                        portName = portName,
                        tempInterval = tempInterval,
                        stateInterval = stateInterval,
                        dataTimeout = dataTimeout,
                        hedgeIdList = hedgeIdList,
                        logger = logger)
            }
            .also(this::register)
    }
}
