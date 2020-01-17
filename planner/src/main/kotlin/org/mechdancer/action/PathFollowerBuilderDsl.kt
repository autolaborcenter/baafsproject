package org.mechdancer.action

import org.mechdancer.annotations.BuilderDslMarker
import org.mechdancer.common.shape.Circle
import org.mechdancer.common.shape.Shape
import org.mechdancer.geometry.angle.Angle
import org.mechdancer.geometry.angle.toDegree
import org.mechdancer.geometry.transformation.Pose2D
import org.mechdancer.geometry.transformation.pose2D
import org.mechdancer.remote.presets.RemoteHub
import kotlin.math.PI

@BuilderDslMarker
class PathFollowerBuilderDsl
private constructor() {
    var sensorPose: Pose2D = pose2D(.275, 0)
    var lightRange: Shape = Circle(.3)
    var minTipAngle: Angle = 60.toDegree()
    var minTurnAngle: Angle = 15.toDegree()
    var turnThreshold: Angle = 180.toDegree()
    var maxSpeed: Double = .2

    var painter: RemoteHub? = null

    companion object {
        fun pathFollower(block: PathFollowerBuilderDsl. () -> Unit = {}) =
            PathFollowerBuilderDsl()
                .apply(block)
                .apply {
                    require(minTipAngle.asRadian() in .0..PI)
                    require(minTurnAngle.asRadian() in .0..PI)
                    require(maxSpeed > 0)
                }
                .run {
                    VirtualLightSensorPathFollower(
                            sensor = VirtualLightSensor(
                                    onRobot = sensorPose,
                                    lightRange = lightRange),
                            minTipAngle = minTipAngle,
                            minTurnAngle = minTurnAngle,
                            turnThreshold = turnThreshold,
                            maxSpeed = maxSpeed
                    ).also { it.painter = painter }
                }
    }
}
