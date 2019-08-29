package cn.autolabor.pm1

import cn.autolabor.Resource
import cn.autolabor.pm1.sdk.Odometry
import cn.autolabor.pm1.sdk.PM1

/**
 * PM1 底盘驱动资源控制器
 * 用户可自选调度器，反复调用 [invoke] 方法以运行
 */
class Resource(private val callback: (Odometry) -> Unit) : Resource {
    override val info = PM1.initialize()

    override fun invoke() {
        callback(PM1.odometry.copy(stamp = System.currentTimeMillis()))
        Thread.sleep(100)
    }

    override fun close() {
        PM1.safeShutdown()
    }
}
