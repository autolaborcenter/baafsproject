package cn.autolabor.localplanner

import org.mechdancer.algebra.doubleEquals
import org.mechdancer.algebra.function.vector.*
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.algebra.implement.vector.vector2DOfZero
import org.mechdancer.common.Odometry
import org.mechdancer.geometry.angle.toAngle
import kotlin.math.max

class PotentialFieldLocalPlanner(
    private val attractRange: Double,
    private val repelRange: Double,
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
            val (p0, _) = pose

            while (list.last().p euclid p0 < attractRange)
                list += globalIter.consume() ?: break

            var dn = list.first().p euclid p0
            while (true) {
                val `dn-1` = dn
                dn = list.getOrNull(1)?.p?.euclid(p0) ?: break
                if (dn < `dn-1`) list.removeAt(0)
                else break
            }

            val fa = list.fold(vector2DOfZero()) { sum, (p, _) ->
                sum + (p - p0).to2D()
            }
            val fr = repulsionPoints.fold(vector2DOfZero()) { sum, p ->
                val v = (p0 - p)
                val l = v.norm()
                when {
                    l > repelRange -> sum
                    else           -> sum + v * (1 / l - 1 / repelRange) / (l * l * l)
                }
            }.let { (x, y) -> vector2DOf(max(x, .0), y) }
            val f = (fa * ka + fr).normalize().to2D()
            if (doubleEquals(f.norm(), .0)) break
            pose = Odometry(p0 + f * step, fa.toAngle())
//            println("fa = ${fa.rowView()}, fr = ${fr.rowView()}, pose = $pose")
            yield(pose)
        }
    }

    private companion object {
        fun <T> Iterator<T>.consume() =
            takeIf(Iterator<*>::hasNext)?.next()
    }
}
