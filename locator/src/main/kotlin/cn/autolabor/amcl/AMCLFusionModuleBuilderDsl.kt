package cn.autolabor.amcl

import cn.autolabor.amcl.AMCLFilterBuilderDsl.Companion.AMCLFilterBuild
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.mechdancer.SimpleLogger
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.annotations.BuilderDslMarker
import org.mechdancer.common.Stamped
import org.mechdancer.geometry.transformation.Pose2D
import org.mechdancer.geometry.transformation.pose2D
import org.mechdancer.paint
import org.mechdancer.paintPose
import org.mechdancer.paintPoses
import org.mechdancer.remote.presets.RemoteHub

@BuilderDslMarker
class AMCLFusionModuleBuilderDsl private constructor() {
    // 滤波器配置
    private var filterBlocks = mutableListOf<AMCLFilterBuilderDsl.() -> Unit>()

    fun filter(block: AMCLFilterBuilderDsl.() -> Unit) {
        filterBlocks.add(block)
    }

    var logger: SimpleLogger? = SimpleLogger("LocationFusionModule")
    var painter: RemoteHub? = null

    companion object {
        /**
         * 在指定作用域上启动定位融合协程
         */
        @ExperimentalCoroutinesApi
        fun CoroutineScope.startLocationFusion(
            robotOnOdometry: ReceiveChannel<Stamped<Pose2D>>,
            beaconOnMap: ReceiveChannel<Stamped<Vector2D>>,
            robotOnMap: SendChannel<Stamped<Pose2D>>,
            block: AMCLFusionModuleBuilderDsl.() -> Unit = {}
        ) {
            AMCLFusionModuleBuilderDsl()
                .apply(block)
                .run {
                    // 构造滤波器
                    val filter = AMCLFilterBuild { for (f in this@run.filterBlocks) f(this) }
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
                                .takeIf { it != null }
                                ?.also { robotOnMap.send(it) }
                                ?.also { (_, data) ->
                                    logger?.log(data.p.x, data.p.y, data.d.rad)
                                    painter?.run {
                                        paintPose("粒子滤波", data)
                                        with(filter.pf.set.samples) {
                                            paintPoses("粒子群", map { (p, _) -> pose2D(p.x, p.y, p.z) }.take(200))
                                        }
                                    }
                                }
                        }
                    }.invokeOnCompletion { robotOnMap.close() }
                }
        }
    }
}
