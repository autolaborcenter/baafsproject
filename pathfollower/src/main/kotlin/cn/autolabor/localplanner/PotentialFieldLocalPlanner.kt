package cn.autolabor.localplanner

import org.mechdancer.algebra.doubleEquals
import org.mechdancer.algebra.function.vector.*
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.algebra.implement.vector.vector2DOfZero
import org.mechdancer.common.Odometry
import org.mechdancer.common.shape.Shape
import org.mechdancer.geometry.angle.toAngle
import org.mechdancer.geometry.angle.toVector
import kotlin.math.max

class PotentialFieldLocalPlanner(
    private val attractRange: Shape,
    private val repelRange: Shape,
    private val step: Double,
    private val ka: Double
) {
    /**
     * 修饰函数
     *
     * @param global 机器人坐标系上的全局路径
     * @param repulsionPoints 斥力点
     */
    fun modify(
        global: Sequence<Odometry>,
        repulsionPoints: Collection<Vector2D>
    ): Sequence<Odometry> = sequence {
        val globalIter = global.iterator()
        val list = globalIter.consume()?.let { mutableListOf(it) } ?: return@sequence
        var pose = Odometry.pose()
        while (true) {
            val (p0, d0) = pose

            while (list.last().p in attractRange)
                list += globalIter.consume() ?: break

            var dn = list.first().p euclid p0
            while (true) {
                val `dn-1` = dn
                dn = list.getOrNull(1)?.p?.euclid(p0) ?: break
                if (dn < `dn-1`) list.removeAt(0)
                else break
            }

            val fa = list.foldIndexed(vector2DOfZero()) { i, sum, (p, _) ->
                sum + (p - p0).to2D() * (list.size - i)
            }
            val fr = repulsionPoints.fold(vector2DOfZero()) { sum, p ->
                val v = (p0 - p)
                val l = v.norm()
                when (p) {
                    !in repelRange -> sum
                    else           -> sum + v / (l * l * l)
                }
            }.let { (x, y) -> vector2DOf(max(x, .0), y) }
            val f = (fa / ((list.size + 1) * list.size / 2) * ka + fr / repulsionPoints.size).normalize().to2D()
            pose =
                if (doubleEquals(f.norm(), .0))
                    Odometry(p0 + vector2DOf(step, 0), d0)
                else
                    Odometry(p0 + f * step, list.fold(vector2DOfZero()) { sum, it -> sum + it.d.toVector() }.toAngle())
//            println("fa = ${fa.rowView()}, fr = ${fr.rowView()}, f = ${f.rowView()}, pose = $pose")
            yield(pose)
        }
    }.take(50)

    private companion object {
        fun <T> Iterator<T>.consume() =
            takeIf(Iterator<*>::hasNext)?.next()
    }
}
