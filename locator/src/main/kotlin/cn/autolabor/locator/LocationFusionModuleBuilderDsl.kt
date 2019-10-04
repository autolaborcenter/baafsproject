package cn.autolabor.locator

import cn.autolabor.locator.ParticleFilterBuilderDsl.Companion.particleFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.mechdancer.*
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
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

    var logger: SimpleLogger? = SimpleLogger("定位融合")
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
                        filter.stepFeedback.add { (measureWeight, particleWeight) ->
                            paint("定位权重", measureWeight)
                            paint("粒子权重", particleWeight)
                        }
                    }
                    // 使用定位数据
                    launch {
                        for (item in beaconOnMap) {
                            filter.measureHelper(item)
                            if (robotOnOdometry.isClosedForReceive) break
                        }
                    }
                    // 使用里程计数据
                    launch {
                        for (item in robotOnOdometry)
                            filter.measureMaster(item)
                                ?.also { robotOnMap.send(it) }
                                ?.also { (_, data) ->
                                    logger?.log(data.p.x, data.p.y, data.d.asRadian())
                                    painter?.run {
                                        paintPose("粒子滤波", data)
                                        with(filter.particles) {
                                            paintPoses("粒子群", map { (p, _) -> p })
                                            paintVectors("粒子寿命", mapIndexed { i, (_, a) -> vector2DOf(i, a) })
                                        }
                                    }
                                }
                    }.invokeOnCompletion {
                        robotOnMap.close()
                    }
                }
        }
    }
}
