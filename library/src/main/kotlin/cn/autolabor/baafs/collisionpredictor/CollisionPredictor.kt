package cn.autolabor.baafs.collisionpredictor

import kotlinx.coroutines.*
import org.mechdancer.SimpleLogger
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.common.Odometry
import org.mechdancer.common.shape.Polygon
import org.mechdancer.common.toTransformation
import org.mechdancer.paint
import org.mechdancer.paintVectors
import org.mechdancer.remote.presets.RemoteHub
import kotlin.math.max
import kotlin.math.min

/** 碰撞预测器 */
class CollisionPredictor
internal constructor(
    robotOutline: Polygon,

    private val predictingTime: Long,
    private val countToContinue: Int,
    private val countToStop: Int,
    private val obstacleSource: suspend () -> Collection<Vector2D>
) {
    private var count = 0
    private val origin = robotOutline.vertex

    var logger: SimpleLogger? = null
    var painter: RemoteHub? = null

    fun predict(path: (Long) -> Odometry) =
        runBlocking(Dispatchers.Default) {
            val getting = async { obstacleSource() }
            val delta = path(predictingTime).toTransformation()
            val outline = Polygon(origin.map { delta(it).to2D() })
            val points = getting.await()
            count =
                if (points.none { it in outline })
                    min(count + 1, +countToStop)
                else
                    max(count - 1, -countToContinue)
            GlobalScope.launch {
                logger?.log("count = $count")
                painter?.run {
                    paint("R 运动预测", outline)
                    paintVectors("R 碰撞", points.filter { it in outline })
                }
            }
            count > 0
        }
}
