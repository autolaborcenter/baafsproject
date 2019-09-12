package org.mechdancer.modules

import cn.autolabor.BehaviorTree.Behavior.Action
import cn.autolabor.BehaviorTree.Behavior.Waiting
import cn.autolabor.BehaviorTree.Logic
import cn.autolabor.BehaviorTree.Logic.First
import cn.autolabor.BehaviorTree.Logic.LoopEach
import cn.autolabor.BehaviorTree.Result.*
import cn.autolabor.Odometry
import cn.autolabor.pathfollower.VirtualLightSensorPathFollower
import cn.autolabor.pathfollower.VirtualLightSensorPathFollower.FollowCommand
import cn.autolabor.pathfollower.VirtualLightSensorPathFollower.FollowCommand.*
import cn.autolabor.transform.Transformation
import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.algebra.implement.vector.vector2DOfZero
import org.mechdancer.geometry.angle.toAngle
import org.mechdancer.geometry.angle.toDegree
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.geometry.angle.toVector
import kotlin.math.abs

class FollowBehavior(
    private val follower: VirtualLightSensorPathFollower,
    private val control: (Double, Double) -> Unit
) {
    private var command: FollowCommand = Follow(.0, .0)
    private var origin = Odometry()
    private var index = 0L
    private val behaviors = LoopEach<Transformation>(
        Action { transformation ->
            ++index
            command = follower(transformation)
            when (command) {
                is Follow -> Success
                is Turn   -> Success
                Error     -> {
                    println("error")
                    Failure
                }
                Finish    -> {
                    println("finish")
                    Failure
                }
            }
        },
        First(
            Action {
                val temp = command
                if (temp is Follow) {
                    val (v, w) = temp
                    control(v, w)
                    Success
                } else
                    Failure
            },
            Logic.Sequence(
                Action { transformation ->
                    origin = transformation.toOdometry()
                    control(.0, .0)
                    Success
                },
                Waiting(200L),
                Action { transformation ->
                    control(.05, .0)
                    val current = transformation.toOdometry()
                    val delta = current minusState origin
                    if (delta.p.norm() < 0.25)
                        Running
                    else {
                        origin = current
                        Success
                    }
                },
                Action { transformation ->
                    control(.0, 10.0.toDegree().asRadian())
                    val current = transformation.toOdometry()
                    val delta = current minusState origin

                    val temp = command
                    if (temp is Turn) {
                        val (angle) = temp
                        if (abs(delta.d.asRadian()) < abs(angle))
                            Running
                        else
                            Success
                    } else
                        Failure
                })))

    fun follow(transformation: Transformation) {
        val last = index
        behaviors(transformation)
        if (index == last) Thread.sleep(100)
    }

    private companion object {
        fun Transformation.toOdometry() =
            Odometry(
                this(vector2DOfZero()).to2D(),
                invokeLinear(.0.toRad().toVector()).to2D().toAngle()
            )
    }
}
