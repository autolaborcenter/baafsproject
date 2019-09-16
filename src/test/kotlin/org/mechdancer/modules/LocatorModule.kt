package org.mechdancer.modules

import cn.autolabor.Odometry
import cn.autolabor.Stamped
import cn.autolabor.locator.ParticleFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.modules.devices.Default
import org.mechdancer.paint
import org.mechdancer.remote.presets.RemoteHub

/**
 * 在指定作用域上启动定位融合协程
 */
fun CoroutineScope.startLocationFilter(
    robotOnOdometry: ReceiveChannel<Stamped<Odometry>>,
    robotOnLocator: ReceiveChannel<Stamped<Vector2D>>,
    robotOnMap: SendChannel<Stamped<Odometry>>,
    filter: ParticleFilter = Default.filter,
    remote: RemoteHub? = Default.remote
) {
    // 使用里程计数据
    launch {
        while (isActive)
            robotOnOdometry.receive()
                .also { (_, data) -> remote?.paint("里程计", data.p.x, data.p.y, data.d.asRadian()) }
                .let(filter::measureMaster)
                ?.also { (_, data) -> remote?.paint("粒子滤波", data.p.x, data.p.y, data.d.value) }
                ?.also { robotOnMap.send(it) }
    }
    // 使用定位数据
    launch {
        while (isActive)
            robotOnLocator.receive()
                .also(filter::measureHelper)
                .also { (_, data) -> remote?.paint("定位", data.x, data.y) }
    }
}
