package cn.autolabor.pathfollower

import cn.autolabor.pathfollower.PathFollowerModuleDebugerBuilderDsl.Companion.debugPathFollowerModule
import cn.autolabor.pathfollower.shape.Circle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.mechdancer.common.Odometry.Companion.odometry

@ExperimentalCoroutinesApi
fun main() = debugPathFollowerModule {
    // 仿真配置
    speed = 1
    // 导航器配置
    module {
        follower {
            sensorPose = odometry(.16, .0)
            lightRange = Circle(.2, 16)
        }
    }
}
