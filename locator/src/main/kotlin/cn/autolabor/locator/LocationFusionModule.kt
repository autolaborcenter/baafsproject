package cn.autolabor.locator

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.mechdancer.SimpleLogger
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.paint
import org.mechdancer.remote.presets.RemoteHub

/**
 * 在指定作用域上启动定位融合协程
 */
@ExperimentalCoroutinesApi
fun CoroutineScope.startLocationFusion(
    robotOnOdometry: ReceiveChannel<Stamped<Odometry>>,
    beaconOnMap: ReceiveChannel<Stamped<Vector2D>>,
    robotOnMap: SendChannel<Stamped<Odometry>>,
    filter: ParticleFilter,
    logger: SimpleLogger? = SimpleLogger("粒子滤波"),
    painter: RemoteHub? = null
) {
    // 使用定位数据
    launch {
        for (item in beaconOnMap) {
            filter.measureHelper(item)
            if (robotOnOdometry.isClosedForReceive) break
        }
    }
    // 使用里程计数据
    launch {
        for (item in robotOnOdometry) {
            filter.measureMaster(item)
                ?.also { robotOnMap.send(it) }
                ?.also { (_, data) ->
                    logger?.log(data.p.x, data.p.y, data.d.asRadian())
                    painter?.paint("粒子滤波", data.p.x, data.p.y, data.d.value)
                }
        }
        robotOnMap.close()
    }
}
