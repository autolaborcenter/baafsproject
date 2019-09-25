package org.mechdancer.simulation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity.Companion.velocity
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.common.toTransformation
import org.mechdancer.modules.Default.commands
import org.mechdancer.modules.Default.remote
import org.mechdancer.paintVectors
import java.util.concurrent.atomic.AtomicReference

@ExperimentalCoroutinesApi
fun main() = runBlocking {
    val buffer = AtomicReference<NonOmnidirectional>(velocity(0, 0))
    val robot = Chassis(Stamped(0, Odometry()))
    val chassis = listOf(vector2DOf(+.2, +.2),
                         vector2DOf(-.2, +.2),
                         vector2DOf(-.4, +.1),
                         vector2DOf(-.4, -.1),
                         vector2DOf(-.2, -.2),
                         vector2DOf(+.2, -.2)).let { it + it.first() }
    val block = listOf(vector2DOf(-.2, +.2),
                       vector2DOf(-.2, -.2),
                       vector2DOf(+.2, -.2),
                       vector2DOf(+.2, +.2)).let { it + it.first() }
        .let {
            val blockToMap = Odometry.odometry(1, 1, 0).toTransformation()
            it.map(blockToMap::invoke)
        }
    launch {
        for (command in commands) buffer.set(velocity(0.1 * command.v, 0.2 * command.w))
    }
    speedSimulation(this) { buffer.get() }
        .consumeEach { (_, v) ->
            val (_, pose) = robot.drive(v)
            val odometryToRobot = -pose.toTransformation()
            println(v)
            remote.paintVectors("chassis", chassis)
            remote.paintVectors("block", block.map(odometryToRobot::invoke))
        }
}
