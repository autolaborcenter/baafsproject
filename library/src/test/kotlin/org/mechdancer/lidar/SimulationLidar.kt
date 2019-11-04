package org.mechdancer.lidar

import org.mechdancer.common.Odometry
import org.mechdancer.common.Polar
import org.mechdancer.common.Stamped
import org.mechdancer.common.shape.Polygon
import org.mechdancer.common.toTransformation
import org.mechdancer.device.PolarFrameCollectorQueue
import org.mechdancer.simulation.Lidar
import org.mechdancer.simulation.random.Normal

internal class SimulationLidar(
    private val lidar: Lidar,
    private val onRobot: Odometry,
    private val cover: List<Polygon>,
    private val errorSigma: Double
) {
    private val queue = PolarFrameCollectorQueue()

    val toRobot = onRobot.toTransformation()
    val frame get() = queue.get()

    private fun Polar.addError() =
        copy(distance = distance * Normal.next(expect = 1.0, sigma = errorSigma))

    fun update(t: Long, robotOnMap: Odometry, obstacles: List<Polygon>) {
        lidar
            .update(t * 1E-3, robotOnMap, onRobot, cover, obstacles)
            .map { it.data }
            .forEach {
                if (it.distance.isNaN())
                    queue.refresh(it.angle)
                else
                    queue += Stamped(t, it.addError())
            }
    }
}
