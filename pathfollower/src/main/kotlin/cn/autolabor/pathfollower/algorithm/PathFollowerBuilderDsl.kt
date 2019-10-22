package cn.autolabor.pathfollower.algorithm

import cn.autolabor.pathfollower.shape.Circle
import cn.autolabor.pathfollower.shape.Shape
import org.mechdancer.BuilderDslMarker
import org.mechdancer.common.Odometry
import org.mechdancer.common.filters.Filter
import org.mechdancer.geometry.angle.Angle
import org.mechdancer.geometry.angle.toDegree
import org.mechdancer.geometry.angle.toRad
import kotlin.math.PI

@BuilderDslMarker
class PathFollowerBuilderDsl private constructor() {
    var sensorPose: Odometry = Odometry.odometry(0.275, 0)
    var lightRange: Shape = Circle(0.3)
    var controller: Filter<Double, Double> = UnitController
    var minTipAngle: Angle = 60.toDegree()
    var minTurnAngle: Angle = 15.toDegree()
    var maxLinearSpeed: Double = 0.1
    var maxAngularSpeed: Angle = 0.5.toRad()

    companion object {
        fun pathFollower(block: PathFollowerBuilderDsl. () -> Unit) =
            PathFollowerBuilderDsl()
                .apply(block)
                .apply {
                    require(minTipAngle.asRadian() in .0..PI)
                    require(minTurnAngle.asRadian() in .0..PI)
                    require(maxLinearSpeed > 0)
                    require(maxAngularSpeed.asRadian() > 0)
                }
                .run {
                    VirtualLightSensorPathFollower(
                        sensor = VirtualLightSensor(
                            onRobot = sensorPose,
                            lightRange = lightRange),
                        controller = controller,
                        minTipAngle = minTipAngle,
                        minTurnAngle = minTurnAngle,
                        maxLinearSpeed = maxLinearSpeed,
                        maxAngularSpeed = maxAngularSpeed)
                }
    }
}
