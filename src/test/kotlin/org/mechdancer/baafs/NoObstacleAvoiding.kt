package org.mechdancer.baafs

import cn.autolabor.locator.ParticleFilterBuilderDsl.Companion.particleFilter
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
import org.mechdancer.baafs.modules.startPathFollower
import org.mechdancer.channel
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity.NonOmnidirectional

@ExperimentalCoroutinesApi
fun main() {
    val mode = Direct
    // 话题
    val robotOnOdometry = channel<Stamped<Odometry>>()
    val robotOnMap = channel<Stamped<Odometry>>()
    val beaconOnMap = channel<Stamped<Vector2D>>()
    val commandToRobot = channel<NonOmnidirectional>()
    // 任务
    with(CoroutineScope(Dispatchers.Default)) {
        startChassis(
            mode = mode,
            odometry = robotOnOdometry,
            command = commandToRobot)
        startBeacon(
            mode = mode,
            beaconOnMap = beaconOnMap)
        startLocationFusion(
            robotOnOdometry = robotOnOdometry,
            beaconOnMap = beaconOnMap,
            robotOnMap = robotOnMap,
            filter = particleFilter {
                beaconOnRobot = vector2DOf(-0.037, .0)
            })
        startPathFollower(
            robotOnMap = robotOnMap,
            commandOut = commandToRobot,
            remote = null)
        await()
    }
}
