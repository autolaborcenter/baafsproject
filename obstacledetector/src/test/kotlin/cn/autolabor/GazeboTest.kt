package cn.autolabor

import cn.autolabor.core.annotation.SubscribeMessage
import cn.autolabor.core.annotation.TaskFunction
import cn.autolabor.core.annotation.TaskProperties
import cn.autolabor.core.server.ServerManager
import cn.autolabor.core.server.executor.AbstractTask
import cn.autolabor.message.sensor.MsgLidar
import cn.autolabor.plugin.gazebo.conversion.LidarMsgConversion
import cn.autolabor.plugin.gazebo.task.GazeboBridgeTask
import cn.autolabor.util.reflect.TypeNode
import org.mechdancer.algebra.function.matrix.times
import org.mechdancer.algebra.function.vector.div
import org.mechdancer.algebra.function.vector.minus
import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.algebra.function.vector.times
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.common.collection.map2d.CompleteSymmetricPairMap2D
import org.mechdancer.remote.presets.remoteHub
import java.net.InetSocketAddress
import kotlin.math.roundToInt

@TaskProperties
class SubscribeLidar : AbstractTask() {
    private val buffer = DistanceBuffer()
    private val painter = remoteHub(
        name = "obstacle",
        address = InetSocketAddress("238.88.8.100", 30000))
    private var i = 0

    class PointBuffered(polar: Polar) {
        val index = polar.toHexagonal(0.01)
        val pixel = index.toPixel(0.01)
    }

    private val distanceBuffer =
        CompleteSymmetricPairMap2D<Stamped<PointBuffered>, Double>
        { (_, a), (_, b) -> (a.pixel - b.pixel).norm() }
    private var last = 0L

    init {
        painter.openAllNetworks()
        launchBlocking { painter() }
    }

    @SubscribeMessage(topic = "scan")
    @TaskFunction
    fun printLidar(msgLidar: MsgLidar) {
        val distances = msgLidar.distances
        val angles = msgLidar.angles
        val interval = 1E8 / distances.size
        val time = System.nanoTime()
        val list = List(distances.size) { i ->
            Stamped((time + 1 * interval).toLong(), Polar(distances[i], angles[i]))
        }

//        val head = list.first().time
//        val tail = last
//        last = list.last().time
//        // 删除无效的
//        distanceBuffer.keys0
//            .filter { (time, _) -> time < head }
//            .let { distanceBuffer.removeAll(it) }
//        // 添加新的
//        data.asReversed()
//            .takeWhile { (time, _) -> tail < time }
//            .forEach { (time, data) ->
//                distanceBuffer.put(Stamped(time, cn.autolabor.DistanceBuffer.PointBuffered(data)))
//            }

        list.map { (_, data) -> vector2DOf(data.x, data.y) }
            .apply { painter.paintFrame2("gazebo", map { it.x to it.y }) }
            .asSequence()
            .map { (hexTransform * it).to2D() / 0.05 }
            .map { it.x.roundToInt() to it.y.roundToInt() }
            .distinct()
            .map { (x, y) -> (pixelTransform * vector2DOf(x, y)).to2D() * 0.05 }
            .map { it.x to it.y }
            .toList()
            .also { painter.paintFrame2("mapped", it) }
        println(++i)
    }
}

fun main() {
    GazeboBridgeTask.me().registerSubscriber("/gazebo/default/pioneer2dx_withLidar/hokuyo/link/laser/scan",
                                             "gazebo.msgs.LaserScanStamped",
                                             "scan",
                                             TypeNode(MsgLidar::class.java),
                                             LidarMsgConversion())
    ServerManager.me().register(SubscribeLidar())
}
