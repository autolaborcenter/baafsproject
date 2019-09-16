package org.mechdancer.modules

import cn.autolabor.Odometry
import cn.autolabor.transform.Transformation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.algebra.implement.vector.vector2DOfZero
import org.mechdancer.geometry.angle.toAngle
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.geometry.angle.toVector

fun CoroutineScope.await() {
    runBlocking { this@await.coroutineContext[Job]?.join() }
}

fun Transformation.toPose(): Odometry {
    require(dim == 2) { "pose is a 2d transformation" }
    val p = invoke(vector2DOfZero()).to2D()
    val d = invokeLinear(.0.toRad().toVector()).to2D().toAngle()
    return Odometry(p, d)
}

fun Odometry.toTransformation() =
    Transformation.fromPose(p, d)
