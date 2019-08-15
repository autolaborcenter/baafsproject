package cn.autolabor.locator

import cn.autolabor.utilities.Odometry
import cn.autolabor.utilities.time.MatcherBase
import cn.autolabor.utilities.time.Stamped
import org.mechdancer.algebra.function.vector.plus
import org.mechdancer.algebra.function.vector.times
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.geometry.angle.rotate
import org.mechdancer.geometry.angle.times

/**
 * 粒子滤波器
 */
class ParticleFilter
    : Mixer<
    Stamped<Odometry>,
    Stamped<Vector2D>,
    Odometry> {
    private val matcher = MatcherBase<Stamped<Odometry>, Stamped<Vector2D>>()
    private val particles = listOf<Odometry>()

    override fun measureMaster(item: Stamped<Odometry>) =
        matcher.add1(item).also { update() }

    override fun measureHelper(item: Stamped<Vector2D>) =
        matcher.add2(item).also { update() }

    private fun update() {
        // 为配对插值
        val newPairs =
            generateSequence { matcher.match2() }
                .mapNotNull { (measure, before, after) ->
                    (after.time - before.time)
                        .takeIf { it in 1..500 }
                        ?.let {
                            val k = (measure.time - before.time) / it
                            measure.data to Odometry(
                                before.data.s * k + after.data.s * (1 - k),
                                before.data.a * k + after.data.s * (1 - k),
                                before.data.p * k + after.data.p * (1 - k),
                                before.data.d * k rotate after.data.d * (1 - k))
                        }
                }
                .toList()

        if (particles.isEmpty()) {
            initialize(newPairs)
            return
        }
    }

    private fun initialize(list: List<Pair<*, *>>) {
        for ((measure, state) in list)
            println("$measure, $state")
    }

    override operator fun get(item: Stamped<Odometry>) = null
}
