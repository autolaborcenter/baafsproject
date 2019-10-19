package cn.autolabor.locator

import cn.autolabor.locator.ParticleFilterBuilderDsl.Companion.particleFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.mechdancer.*
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.remote.presets.RemoteHub

@BuilderDslMarker
class LocationFusionModuleBuilderDsl private constructor() {
    // 滤波器配置
    var filter = particleFilter {}

    fun filter(block: ParticleFilterBuilderDsl.() -> Unit) {
        filter = particleFilter(block)
    }

    var logger: SimpleLogger? = SimpleLogger("LocationFusionModule")
    var painter: RemoteHub? = null

    companion object {
        /**
         * 在指定作用域上启动定位融合协程
         */
        @ExperimentalCoroutinesApi
        fun CoroutineScope.startLocationFusion(
            robotOnOdometry: ReceiveChannel<Stamped<Odometry>>,
            beaconOnMap: ReceiveChannel<Stamped<Vector2D>>,
            robotOnMap: SendChannel<Stamped<Odometry>>,
            block: LocationFusionModuleBuilderDsl.() -> Unit = {}
        ) {
            LocationFusionModuleBuilderDsl()
                .apply(block)
                .run {
                    painter?.run {
                        filter.stepFeedback.add { (t, state) ->
                            val time = t.toDouble()
                            val (measureWeight, particleWeight, quality) = state
                            paint("定位权重", time, measureWeight)
                            paint("粒子权重", time, particleWeight)

                            val (age, p, d) = quality
                            paint("稳定性质量", time, age)
                            paint("位置一致性质量", time, p)
                            paint("方向一致性质量", time, d)
                        }
                    }
                    // 使用定位数据
                    launch {
                        for (item in beaconOnMap) {
                            painter?.paint("定位", item.data.x, item.data.y)
                            filter.measureHelper(item)
                            if (robotOnOdometry.isClosedForReceive) break
                        }
                    }
                    // 使用里程计数据
                    launch {
                        for (item in robotOnOdometry) {
                            painter?.paintPose("里程计", item.data)
                            filter.measureMaster(item)
                                ?.also { robotOnMap.send(it) }
                                ?.also { (_, data) ->
                                    logger?.log(data.p.x, data.p.y, data.d.asRadian())
                                    painter?.run {
                                        paintPose("粒子滤波", data)
                                        with(filter.particles) {
                                            paintPoses("粒子群", map { (p, _) -> p })
                                        }
                                    }
                                }
                        }
                    }.invokeOnCompletion { robotOnMap.close() }
                }
        }
    }
}
