package cn.autolabor.baafs

import com.faselase.LidarSet
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.mechdancer.SimpleLogger
import org.mechdancer.algebra.core.Vector
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.common.Odometry
import org.mechdancer.common.shape.Polygon
import org.mechdancer.common.toTransformation
import org.mechdancer.paint
import org.mechdancer.paintVectors
import org.mechdancer.remote.presets.RemoteHub
import kotlin.math.max
import kotlin.math.min

class CollisionPredictor internal constructor(
    private val lidarSet: LidarSet,
    robotOutline: Polygon,

    private val predictingTime: Long,
    private val countToContinue: Int,
    private val countToStop: Int,
    private val logger: SimpleLogger?,
    private val painter: RemoteHub?
) {
    private var count = 0
    private val origin = robotOutline.vertex

    suspend fun predict(path: (Long) -> Odometry): Boolean {
        val delta = path(predictingTime).toTransformation()
        val getting = coroutineScope { async { lidarSet.frame } }
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
        painter?.run {
            paint("R 运动预测", outline)
            paintVectors("R 雷达", points)
            paintVectors("R 碰撞", points.filter { it in outline })
        }
        return count > 0
    }
}
