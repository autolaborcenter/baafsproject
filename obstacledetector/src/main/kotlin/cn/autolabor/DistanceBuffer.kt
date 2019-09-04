package cn.autolabor

import org.mechdancer.algebra.function.vector.minus
import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.common.collection.map2d.CompleteSymmetricPairMap2D
import org.mechdancer.common.collection.map2d.IMap2D

/**
 * 砝石资源包装，自动规范化到六角格，并缓存各点之间距离
 */
class DistanceBuffer {
    class PointBuffered(polar: Polar) {
        val index = polar.toHexagonal(0.01)
        val pixel = index.toPixel(0.01)
    }

    private val distanceBuffer =
        CompleteSymmetricPairMap2D<Stamped<PointBuffered>, Double>
        { (_, a), (_, b) -> (a.pixel - b.pixel).norm() }
    private var last = 0L

    val buffer: IMap2D<Stamped<PointBuffered>, Stamped<PointBuffered>, Double>
        get() = distanceBuffer

    operator fun invoke(data: List<Stamped<Polar>>) {
        val head = data.first().time
        val tail = last
        last = data.last().time
        // 删除无效的
        distanceBuffer.keys0
            .filter { (time, _) -> time < head }
            .let { distanceBuffer.removeAll(it) }
        // 添加新的
        data.asReversed()
            .takeWhile { (time, _) -> tail < time }
            .forEach { (time, data) -> distanceBuffer.put(Stamped(time, PointBuffered(data))) }
    }
}


