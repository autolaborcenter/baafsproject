package cn.autolabor.baafs

import com.faselase.FaselaseLidarSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.mechdancer.BuilderDslMarker
import org.mechdancer.algebra.core.Vector
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.common.Velocity.NonOmnidirectional
import org.mechdancer.common.shape.Polygon
import org.mechdancer.common.toTransformation
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.exceptions.ExceptionMessage.Occurred
import org.mechdancer.exceptions.ExceptionMessage.Recovered
import org.mechdancer.paint
import org.mechdancer.paintVectors
import org.mechdancer.remote.presets.RemoteHub
import kotlin.math.max
import kotlin.math.min

@BuilderDslMarker
class CollisionPredictingModuleBuilderDsl {
    var predictingTime: Long = 100L
    var countToContinue: Int = 5
    var countToStop: Int = 5
    var painter: RemoteHub? = null

    companion object {
        fun CoroutineScope.startCollisionPredictingModule(
            commandIn: ReceiveChannel<NonOmnidirectional>,
            exception: SendChannel<ExceptionMessage>,
            lidarSet: FaselaseLidarSet,
            robotOutline: Polygon,
            block: CollisionPredictingModuleBuilderDsl.() -> Unit
        ) {
            CollisionPredictingModuleBuilderDsl()
                .apply(block)
                .apply {
                    require(predictingTime > 0)
                    require(countToContinue >= 0)
                    require(countToStop >= 0)
                }
                .run {
                    var count = 0
                    val origin = robotOutline.vertex
                    launch {
                        for (command in commandIn) {
                            val delta = command.toDeltaOdometry(predictingTime / 1000.0).toTransformation()
                            val getting = async { lidarSet.frame }
                            val outline = origin
                                .asSequence()
                                .map(delta::invoke)
                                .map(Vector::to2D)
                                .toList()
                                .let(::Polygon)
                            val points = getting.await()
                            count =
                                if (points.any { it in outline })
                                    min(count + 1, +countToStop)
                                else
                                    max(count - 1, -countToContinue)
                            if (count > 0)
                                exception.send(Recovered(CollisionDetectedException))
                            else
                                exception.send(Occurred(CollisionDetectedException))
                            painter?.run {
                                paint("机器人轮廓", robotOutline)
                                paint("运动预测", outline)
                                paintVectors("障碍物", points)
                            }
                        }
                    }
                }
        }
    }
}
