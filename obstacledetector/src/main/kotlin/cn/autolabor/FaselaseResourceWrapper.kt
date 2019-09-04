package cn.autolabor

import org.mechdancer.algebra.function.vector.minus
import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.common.collection.map2d.CompleteSymmetricPairMap2D

class FaselaseResourceWrapper : Resource {
    private class PointBuffered(polar: Polar) {
        val pixel: Vector2D
        val index: Pair<Int, Int>

        init {
            index = polar.toHexagonal(0.01)
            pixel = index.toPixel(0.01)
        }
    }

    private val distanceBuffer =
        CompleteSymmetricPairMap2D<Stamped<PointBuffered>, Double>
        { (_, a), (_, b) -> (a.pixel - b.pixel).norm() }
    private var last = 0L

    private val core = com.faselase.Resource { list ->
        val head = list.first().time
        val tail = last
        last = list.last().time
        // 删除无效的
        distanceBuffer.keys0
            .filter { (time, _) -> time < head }
            .let { distanceBuffer.removeAll(it) }
        // 添加新的
        list.asReversed()
            .takeWhile { (time, _) -> tail < time }
            .forEach { (time, data) -> distanceBuffer.put(Stamped(time, PointBuffered(data))) }
    }

    override val info get() = core.info
    override fun invoke() = core()
    override fun close() = core.close()
}


