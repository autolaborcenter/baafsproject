package org.mechdancer.modules

import cn.autolabor.locator.ParticleFilter
import cn.autolabor.locator.ParticleFilterBuilder.Companion.particleFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.modules.Default.loggers
import org.mechdancer.paint
import org.mechdancer.remote.presets.RemoteHub

/**
 * 在指定作用域上启动定位融合协程
 */
fun CoroutineScope.startLocationFilter(
    robotOnOdometry: ReceiveChannel<Stamped<Odometry>>,
    robotOnLocator: ReceiveChannel<Stamped<Vector2D>>,
    robotOnMap: SendChannel<Stamped<Odometry>>,
    filter: ParticleFilter = particleFilter {},
    remote: RemoteHub? = Default.remote
) {
    // 使用里程计数据
    launch {
        for (item in robotOnOdometry) {
            item.also { (_, data) ->
                loggers.getLogger("里程计").log(data.p.x, data.p.y, data.d.asRadian())
                remote?.paint("里程计", data.p.x, data.p.y, data.d.asRadian())
            }
            filter.measureMaster(item)
                ?.also { robotOnMap.send(it) }
                ?.also { (_, data) ->
                    loggers.getLogger("粒子滤波").log(data.p.x, data.p.y, data.d.asRadian())
                    remote?.paint("粒子滤波", data.p.x, data.p.y, data.d.value)
                }
        }
    }
    // 使用定位数据
    launch {
        for (item in robotOnLocator) {
            filter.measureHelper(item)
            val (_, data) = item
            loggers.getLogger("定位").log(data.x, data.y)
            remote?.paint("定位", data.x, data.y)
        }
    }
}
