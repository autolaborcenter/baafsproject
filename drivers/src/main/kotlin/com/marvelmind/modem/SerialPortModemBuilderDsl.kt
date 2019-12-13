package com.marvelmind.modem

import cn.autolabor.serialport.manager.SerialPortManager
import com.marvelmind.mobilebeacon.MobileBeaconData
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
class SerialPortModemBuilderDsl private constructor() {
    // 指定串口名字
    var portName: String? = null
    var tempInterval = 60*1000L     // 设置温度周期
    var stateInterval = 10*60*1000L // 读标签状态周期(电压)
    var dataTimeout = 2000L         // 数据超时时间
    var hedgeIdList = ByteArray(0) // 移动标签id列表
    var logger = SimpleLogger("Marvelmind_Modem")   // 运行日志

    companion object {
        /**
         * @param thermometer 输入温湿度数据
         * @param hedgehog 输入定位数据
         * @param exceptions 输出异常信息
         * @param block 配置参数
         */
        fun SerialPortManager.registerModem(
            thermometer: ReceiveChannel<Stamped<Pair<Double, Double>>>,
            hedgehog: ReceiveChannel<Stamped<MobileBeaconData>>,
            exceptions: SendChannel<ExceptionMessage>,
            block: SerialPortModemBuilderDsl.() -> Unit = {}
        ) = SerialPortModemBuilderDsl()
            .apply(block)
            .apply {
                require(dataTimeout > 0)
            }
            .run {
                SerialPortModem(
                    thermometer = thermometer,
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
