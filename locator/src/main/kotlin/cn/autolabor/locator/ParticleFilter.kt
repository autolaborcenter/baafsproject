package cn.autolabor.locator

import cn.autolabor.utilities.Odometry
import cn.autolabor.utilities.time.MatcherBase
import cn.autolabor.utilities.time.Stamped
import org.mechdancer.algebra.function.vector.*
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOfZero
import org.mechdancer.geometry.angle.rotate
import org.mechdancer.geometry.angle.times
import org.mechdancer.geometry.angle.toRad
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 粒子滤波器
 */
class ParticleFilter(private val size: Int)
    : Mixer<
    Stamped<Odometry>,
    Stamped<Vector2D>,
    Odometry> {
    private val matcher = MatcherBase<Stamped<Odometry>, Stamped<Vector2D>>()
    private var particles = emptyList<Odometry>()

    override fun measureMaster(item: Stamped<Odometry>) =
        matcher.add1(item).also { update() }

    override fun measureHelper(item: Stamped<Vector2D>) =
        matcher.add2(item).also { update() }

    private var stateSave: Pair<Vector2D, Odometry>? = null
    private var expectation = Odometry(.0, .0, vector2DOfZero(), .0.toRad())

    private fun update() {
        generateSequence { matcher.match2() }
            // 插值
            .mapNotNull { (measure, before, after) ->
                (after.time - before.time)
                    .takeIf { it in 1..500 }
                    ?.let {
                        val k = (measure.time - before.time).toDouble() / it
                        measure.data to before.data * k + after.data * (1 - k)
                    }
            }
            // 计算
            .forEach { (measure, state) ->
                // 判断第一帧
                val (lastMeasure, lastState) =
                    stateSave ?: run {
                        initialize(measure, state)
                        return@forEach
                    }

                // 计算控制量
                val delta = state minusState lastState
                val dM = measure - lastMeasure
                stateSave = measure to state
                if (abs(delta.p.norm() - dM.norm()) > 0.2) return@forEach

                // 更新粒子群
                particles = particles.map { it plusDelta delta }
                // 计算权重
                val weights = particles.map {
                    1 - min((it.p - measure).norm(), AcceptRange) / AcceptRange
                }
                // 计算方差
                val sum = weights
                              .sum()
                              .takeIf { it > 1 }
                          ?: run {
                              initialize(measure, state)
                              return@forEach
                          }

                var eP = vector2DOfZero()
                var eD = .0
                var eD2 = .0
                particles.forEachIndexed { i, (_, _, p, d) ->
                    val k = weights[i]
                    eP += p * k
                    eD += k * d.value
                    eD2 += k * d.value * d.value
                }
                eP = (eP + measure) / (sum + 1)
                eD /= sum
                eD2 /= sum
                val sigma = sqrt((eD2 - eD * eD) clamp 0.1..0.49)

                val random = java.util.Random()

                particles = particles.mapIndexed { i, item ->
                    if (weights[i] < 0.2) {
                        Odometry(.0, .0, eP, (random.nextGaussian() * sigma + eD).toRad())
                    } else item
                }

                expectation = Odometry(.0, .0, eP, eD.toRad())
            }
    }

    private fun initialize(measure: Vector2D, state: Odometry) {
        stateSave = measure to state
        val step = 2 * PI / size
        particles = List(size) { Odometry(state.s, state.a, measure, (it * step).toRad()) }
    }

    override operator fun get(item: Stamped<Odometry>) =
        stateSave
            ?.second
            ?.let { expectation plusDelta (item.data minusState it) }

    private companion object {
        const val AcceptRange = 0.2

        operator fun Odometry.times(k: Double) =
            Odometry(s * k, a * k, p * k, d * k)

        operator fun Odometry.plus(other: Odometry) =
            Odometry(s + other.s, a + other.a, p + other.p, d rotate other.d)

        infix fun <T : Comparable<T>> T.clamp(range: ClosedRange<T>) =
            when {
                this < range.start        -> range.start
                this > range.endInclusive -> range.endInclusive
                else                      -> this
            }
    }
}
