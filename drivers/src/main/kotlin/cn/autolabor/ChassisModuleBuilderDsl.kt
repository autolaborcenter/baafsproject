package cn.autolabor

import cn.autolabor.PM1.ParameterId.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.mechdancer.BuilderDslMarker
import org.mechdancer.SimpleLogger
import org.mechdancer.WatchDog
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Stamped.Companion.stamp
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.exceptions.device.DeviceNotExistException

@BuilderDslMarker
class ChassisModuleBuilderDsl private constructor() {
    var port: String? = null
    var period: Long = 30L
    var controlTimeout: Long = 300L

    var leftRadius: Double? = null
    var rightRadius: Double? = null
    var width: Double? = null
    var length: Double? = null

    companion object {
        @ExperimentalCoroutinesApi
        fun CoroutineScope.startChassis(
            odometry: SendChannel<Stamped<Odometry>>,
            command: ReceiveChannel<NonOmnidirectional>,
            block: ChassisModuleBuilderDsl.() -> Unit = {}
        ) {
            val parameters =
                ChassisModuleBuilderDsl()
                    .apply(block)
                    .apply {
                        // 初始化 PM1
                        try {
                            PM1.initialize(port ?: "")
                        } catch (e: RuntimeException) {
                            throw DeviceNotExistException("pm1 chassis", e.message)
                        }
                        PM1.locked = false
                        PM1.setCommandEnabled(false)
                        // 配置参数
                        leftRadius?.let { PM1[LeftRadius] = it }
                        rightRadius?.let { PM1[RightRadius] = it }
                        width?.let { PM1[Width] = it }
                        length?.let { PM1[Length] = it }
                    }
            // 启动里程计发送
            launch {
                val logger = SimpleLogger("ChassisOdometry")
                while (isActive) {
                    val (t, data) = PM1.odometry
                    odometry.send(stamp(data))
                    logger.log(t, data.p.x, data.p.y, data.d.asRadian())
                    delay(parameters.period)
                }
            }.invokeOnCompletion { odometry.close() }
            // 启动指令接收
            launch {
                val logger = SimpleLogger("ChassisCommand")
                val watchDog = WatchDog(parameters.controlTimeout)
                for ((v, w) in command) {
                    PM1.drive(v, w)
                    logger.log(v, w)
                    // 取得底盘控制权，但超时无指令则交出控制权
                    launch {
                        PM1.setCommandEnabled(true)
                        if (!watchDog.feed()) {
                            PM1.setCommandEnabled(false)
                            logger.log("give up control")
                        }
                    }
                }
            }
        }
    }
}
