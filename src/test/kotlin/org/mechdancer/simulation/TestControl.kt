package org.mechdancer.simulation

import cn.autolabor.pathmaneger.PathManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.common.Odometry
import org.mechdancer.common.Odometry.Companion.odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.Velocity.Companion.velocity
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.common.invoke
import org.mechdancer.common.toTransformation
import org.mechdancer.modules.Default.commands
import org.mechdancer.modules.Default.remote
import org.mechdancer.paintPoses
import org.mechdancer.paintVectors
import java.util.concurrent.atomic.AtomicReference

private fun shape(vararg vertex: Vector2D) =
    vertex.toList().let { it + it.first() }

private fun Iterable<Vector2D>.put(pose: Odometry) =
    map(pose.toTransformation()::invoke)

@ExperimentalCoroutinesApi
fun main() = runBlocking {
    val buffer = AtomicReference<NonOmnidirectional>(velocity(0, 0))
    val robot = Chassis(Stamped(0, Odometry()))
    val chassis = shape(vector2DOf(+.2, +.2),
                        vector2DOf(-.2, +.2),
                        vector2DOf(-.4, +.1),
                        vector2DOf(-.4, -.1),
                        vector2DOf(-.2, -.2),
                        vector2DOf(+.2, -.2))
    val block = shape(vector2DOf(-.2, +.2),
                      vector2DOf(-.2, -.2),
                      vector2DOf(+.2, -.2),
                      vector2DOf(+.2, +.2)).put(odometry(1, 1, 0))
    val path = PathManager(0.05)
    launch { for (command in commands) buffer.set(velocity(0.1 * command.v, 0.5 * command.w)) }
    speedSimulation(this) { buffer.get() }
        .consumeEach { (_, v) ->
            val (_, pose) = robot.drive(v)
            val odometryToRobot = -pose.toTransformation()
            path.record(pose)

            remote.paintVectors("chassis", chassis)
            remote.paintVectors("block", block.map(odometryToRobot::invoke))
            remote.paintPoses("path", path.get().takeLast(40).map { odometryToRobot(it) })
        }
}
