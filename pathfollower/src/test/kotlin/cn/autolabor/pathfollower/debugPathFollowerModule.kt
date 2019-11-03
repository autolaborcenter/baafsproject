package cn.autolabor.pathfollower

import cn.autolabor.pathfollower.PathFollowerModuleDebugerBuilderDsl.Companion.debugPathFollowerModule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.mechdancer.common.Odometry
import org.mechdancer.common.shape.Circle
import org.mechdancer.geometry.angle.toRad

@ExperimentalCoroutinesApi
fun main() = debugPathFollowerModule {
    // 仿真配置
    speed = 1
    // 导航器配置
    module {
        follower {
            sensorPose = Odometry.pose(.16, .0)
            lightRange = Circle(.2, 16)

            controller = Proportion(.8)

            maxLinearSpeed = .1
            maxAngularSpeed = .5.toRad()
        }
    }
}
