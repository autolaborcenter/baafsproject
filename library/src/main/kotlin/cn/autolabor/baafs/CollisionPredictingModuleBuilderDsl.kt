package cn.autolabor.baafs

import com.faselase.LidarSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mechdancer.BuilderDslMarker
import org.mechdancer.SimpleLogger
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
    var predictingTime: Long = 1000L
    var countToContinue: Int = 5
    var countToStop: Int = 5
    var logger: SimpleLogger? = SimpleLogger("CollisionPredictingModule")
    var painter: RemoteHub? = null

    companion object {
        fun CoroutineScope.startCollisionPredictingModule(
            commandIn: ReceiveChannel<NonOmnidirectional>,
            exception: SendChannel<ExceptionMessage>,
            lidarSet: LidarSet,
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
                    var updateTime = System.currentTimeMillis()
                    painter?.run {
                        launch {
                            while (true) {
                                while (System.currentTimeMillis() - updateTime > 2000L) {
                                    paintVectors("R 雷达", lidarSet.frame)
                                    delay(100L)
                                }
                                delay(2000L)
                            }
                        }
                    }
                    launch {
                        for (command in commandIn) {
                            updateTime = System.currentTimeMillis()
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
                                if (points.none { it in outline })
                                    min(count + 1, +countToStop)
                                else
                                    max(count - 1, -countToContinue)
                            logger?.log("count = $count")
                            if (count > 0)
                                exception.send(Recovered(CollisionDetectedException))
                            else
                                exception.send(Occurred(CollisionDetectedException))
                            painter?.run {
                                paint("R 运动预测", outline)
                                paintVectors("R 雷达", points)
                                paintVectors("R 碰撞", points.filter { it in outline })
                            }
                        }
                    }
                }
        }
    }
}
