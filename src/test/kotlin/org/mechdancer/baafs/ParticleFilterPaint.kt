package org.mechdancer.baafs

import cn.autolabor.locator.ParticleFilterBuilderDsl
import cn.autolabor.locator.startLocationFusion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.baafs.modules.LinkMode.Direct
import org.mechdancer.baafs.modules.await
import org.mechdancer.baafs.modules.startBeacon
import org.mechdancer.baafs.modules.startChassis
import org.mechdancer.channel
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity.NonOmnidirectional

@ExperimentalCoroutinesApi
fun main() {
    // 话题
    val robotOnOdometry = channel<Stamped<Odometry>>()
    val beaconOnMap = channel<Stamped<Vector2D>>()
    val robotOnMap = channel<Stamped<Odometry>>()
    val commandToRobot = channel<NonOmnidirectional>()
    // 任务
    with(CoroutineScope(Dispatchers.Default)) {
        startChassis(
            mode = Direct,
            odometry = robotOnOdometry,
            command = commandToRobot)
        startBeacon(
            mode = Direct,
            beaconOnMap = beaconOnMap)
        startLocationFusion(
            robotOnOdometry = robotOnOdometry,
            beaconOnMap = beaconOnMap,
            robotOnMap = robotOnMap,
            filter = ParticleFilterBuilderDsl.particleFilter {
                beaconOnRobot = vector2DOf(-0.3, .0)
            })
//        launch {
//            val topic = ServerManager.me().getOrCreateMessageHandle("fusion", TypeNode(Msg2DOdometry::class.java))
//            for ((_, data) in robotOnMap) {
//                val (p, d) = data
//                topic.pushSubData(Msg2DOdometry(Msg2DPose(p.x, p.y, d.asRadian()), Msg2DTwist()))
//            }
//        }
        await()
    }
}
