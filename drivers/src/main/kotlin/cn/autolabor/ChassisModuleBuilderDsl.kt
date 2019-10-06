package cn.autolabor

import cn.autolabor.PM1.ParameterId.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.mechdancer.BuilderDslMarker
import org.mechdancer.SimpleLogger
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Stamped.Companion.stamp
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.exceptions.DeviceNotExistException
import java.util.concurrent.atomic.AtomicLong

@BuilderDslMarker
class ChassisModuleBuilderDsl private constructor() {
    var port: String = ""
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
            ChassisModuleBuilderDsl()
                .apply(block)
                .run {
                    // 初始化 PM1
                    PM1.runCatching {
                        initialize(port)
                    }.onFailure { e ->
                        throw e.takeUnless { it is RuntimeException }
                              ?: DeviceNotExistException("pm1 chassis")
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
                while (isActive) {
                    val (x, y, theta) = PM1.odometry
                    odometry.send(stamp(Odometry.odometry(x, y, theta)))
                    delay(30L)
                }
            }.invokeOnCompletion { odometry.close() }
            // 启动指令接收
            launch {
                val i = AtomicLong()
                val logger = SimpleLogger("chassis_command")
                for ((v, w) in command) {
                    PM1.drive(v, w)
                    logger.log(v, w)
                    // 取得底盘控制权，但 1 秒无指令则交出控制权
                    launch {
                        PM1.setCommandEnabled(true)
                        val mark = i.incrementAndGet()
                        delay(1000L)
                        if (i.get() == mark) {
                            PM1.setCommandEnabled(false)
                            logger.log("give up control")
                        }
                    }
                }
            }
        }
    }
}
