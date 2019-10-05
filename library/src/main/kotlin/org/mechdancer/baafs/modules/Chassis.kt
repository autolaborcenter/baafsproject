package org.mechdancer.baafs.modules

import cn.autolabor.PM1
import cn.autolabor.PM1.ParameterId.*
import cn.autolabor.core.server.ServerManager
import cn.autolabor.message.navigation.Msg2DOdometry
import cn.autolabor.util.lambda.LambdaFunWithName
import cn.autolabor.util.lambda.function.TaskLambdaFun01
import cn.autolabor.util.reflect.TypeNode
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.mechdancer.SimpleLogger
import org.mechdancer.baafs.modules.LinkMode.Direct
import org.mechdancer.baafs.modules.LinkMode.Framework
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Stamped.Companion.stamp
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.exceptions.DeviceNotExistException
import java.util.concurrent.atomic.AtomicLong

/** 以 [mode] 模式启动底盘 */
@ExperimentalCoroutinesApi
fun CoroutineScope.startChassis(
    mode: LinkMode,
    odometry: SendChannel<Stamped<Odometry>>,
    command: ReceiveChannel<NonOmnidirectional>
) {
    when (mode) {
        Direct    -> {
            // 初始化 PM1
            with(PM1) {
                try {
                    initialize()
                } catch (e: RuntimeException) {
                    throw DeviceNotExistException("pm1 chassis")
                }
                locked = false
                setCommandEnabled(false)
                // 配置参数
                this[LeftRadius] = 0.1026
                this[RightRadius] = 0.1026
                this[Width] = 0.484
                this[Length] = 0.35
            }
            // 启动里程计发送
            launch {
                while (isActive) {
                    val (x, y, theta) = PM1.odometry
                    odometry.send(stamp(Odometry.odometry(x, y, theta)))
                    delay(30L)
                }
            }.invokeOnCompletion {
                odometry.close()
            }
            // 启动指令接收
            launch {
                val i = AtomicLong()
                val logger = SimpleLogger("控制量")
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
                            logger.log("放弃控制权")
                        }
                    }
                }
            }
        }
        Framework ->
            with(ServerManager.me()) {
                getOrCreateMessageHandle(
                    getConfig("PM1Task", "odometryTopic") as? String ?: "odometry",
                    TypeNode(Msg2DOdometry::class.java)
                ).addCallback(
                    LambdaFunWithName(
                        "odometry_handel",
                        object : TaskLambdaFun01<Msg2DOdometry> {
                            override fun run(p0: Msg2DOdometry?) {
                                if (odometry.isClosedForSend) return
                                val data = p0?.pose ?: return
                                launch {
                                    odometry.send(
                                        Stamped(
                                            p0.header.stamp,
                                            Odometry.odometry(data.x, data.y, data.yaw)
                                        )
                                    )
                                }
                            }
                        })
                )
            }
    }
}
