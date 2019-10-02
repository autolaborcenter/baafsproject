package cn.autolabor.pathfollower.algorithm

import cn.autolabor.pathfollower.shape.Circle
import cn.autolabor.pathfollower.shape.Shape
import org.mechdancer.BuilderDslMarker
import org.mechdancer.common.Odometry
import kotlin.math.PI

@BuilderDslMarker
class PathFollowerBuilderDsl private constructor() {
    var sensorPose: Odometry = Odometry.odometry(0.275, 0)
    var lightRange: Shape = Circle(0.3)
    var controller: Controller =
        Controller.unit
    var minTipAngle: Double = PI / 3
    var minTurnAngle: Double = PI / 12
    var maxJumpCount: Int = 20

    companion object {
        fun pathFollower(block: PathFollowerBuilderDsl. () -> Unit) =
            PathFollowerBuilderDsl()
                .apply(block)
                .apply {
                    require(minTipAngle in .0..PI)
                    require(minTurnAngle in .0..PI)
                    require(maxJumpCount > 0)
                }
                .run {
                    VirtualLightSensorPathFollower(
                        sensor = VirtualLightSensor(
                            onRobot = sensorPose,
                            lightRange = lightRange),
                        controller = controller,
                        minTipAngle = minTipAngle,
                        minTurnAngle = minTurnAngle,
                        maxJumpCount = maxJumpCount)
                }
    }
}
