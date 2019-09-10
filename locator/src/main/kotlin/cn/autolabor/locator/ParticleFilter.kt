package cn.autolabor.locator

import cn.autolabor.Stamped
import cn.autolabor.Temporary
import cn.autolabor.Temporary.Operation.DELETE
import cn.autolabor.Temporary.Operation.REDUCE
import cn.autolabor.transform.Transformation
import cn.autolabor.utilities.MatcherBase
import cn.autolabor.utilities.Odometry
import org.mechdancer.algebra.function.vector.*
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.algebra.implement.vector.vector2DOfZero
import org.mechdancer.geometry.angle.rotate
import org.mechdancer.geometry.angle.times
import org.mechdancer.geometry.angle.toRad
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/** 使用固定 [size] 个粒子的粒子滤波器，其校准用定位器位于 [locator] 处 */
class ParticleFilter(private val size: Int,
                     private val locator: Vector2D = vector2DOfZero())
    : Mixer<Stamped<Odometry>, Stamped<Vector2D>, Odometry> {
    private val matcher = MatcherBase<Stamped<Odometry>, Stamped<Vector2D>>()

    // 粒子：位姿 - 寿命
    @Temporary(REDUCE)
    var particles = emptyList<Pair<Odometry, Int>>()

    // 过程参数渗透
    @Temporary(DELETE)
    data class StepState(val measureWeight: Double,
                         val particleWeight: Double,
                         val measure: Vector2D,
                         val state: Odometry)

    @Temporary(DELETE)
    var stepState = StepState(.0, .0, vector2DOfZero(), Odometry())
    @Temporary(DELETE)
    var lastResult: Odometry? = null

    override fun measureMaster(item: Stamped<Odometry>) =
        matcher.add1(item).also { update() }

    override fun measureHelper(item: Stamped<Vector2D>) =
        matcher.add2(item).also { update() }

    private var stateSave: Pair<Vector2D, Odometry>? = null
    private var expectation = Odometry()

    private fun update() {
        generateSequence(matcher::match2)
            // 匹配 ↑
            // 插值 ↓
            .mapNotNull { (measure, before, after) ->
                (after.time - before.time)
                    // 一对匹配项间隔不应该超过 500 ms
                    .takeIf { it in 1..500 }
                    // 进行线性插值
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
                // 初步过滤
                val lengthM = dM.norm()
                val lengthS = delta.p.norm()
                if (abs(lengthM - lengthS) > 0.2) return@forEach
                // 计算定位权重
                val p0 = (lengthM / 0.2) clamp 0.0..1.0
                val p1 = (abs(lengthM - lengthS) / 0.1) clamp 0.0..1.0
                val measureWeight = size / 2 * (1 - (0.5 * p0 + 0.5 * p1))
                // 更新粒子群
                particles = particles.map { (p, i) -> (p plusDelta delta) to min(i + 1, 10) }
                val locators = particles.map { (odometry, _) ->
                    val (p, d) = odometry
                    val transformation = Transformation.fromPose(p, d)
                    Odometry(transformation(locator).to2D(), d)
                }
                // 计算权重
                val weights = locators.map { (p, _) -> 1 - ((5 * (p - measure).norm()) clamp 0.0..1.0) }
                val sum = weights.sum().takeIf { it > 1 }
                          ?: run {
                              initialize(measure, state)
                              return@forEach
                          }
                @Temporary(DELETE)
                stepState = StepState(measureWeight, sum, measure, state)
                // 计算期望和方差
                var eP = vector2DOfZero()
                var eD = .0
                var eD2 = .0
                locators.forEachIndexed { i, (p, d) ->
                    val k = weights[i]
                    eP += p * k
                    eD += k * d.value
                    eD2 += k * d.value * d.value
                }
                eP = (eP + measure * measureWeight) / (sum + measureWeight)
                eD /= sum
                eD2 /= sum
                val sigma = sqrt((eD2 - eD * eD) clamp 0.1..0.49)
                // 重采样
                val random = java.util.Random()
                val eRobot = Transformation.fromPose(eP, eD.toRad())(-locator).to2D()
                particles = particles.mapIndexed { i, item ->
                    if (weights[i] < 0.2)
                        Odometry(eRobot, (random.nextGaussian() * sigma + eD).toRad()) to 0
                    else item
                }
                // 求期望
                expectation = Odometry(eRobot, eD.toRad())
            }
    }

    // 重新初始化
    private fun initialize(measure: Vector2D, state: Odometry) {
        stateSave = measure to state
        val step = 2 * PI / size
        particles = List(size) {
            val d = (it * step).toRad()
            val p = Transformation.fromPose(measure, d)(-locator).to2D()
            Odometry(p, d) to 0
        }
    }

    override operator fun get(item: Stamped<Odometry>) =
        stateSave
            ?.second
            ?.let { expectation plusDelta (item.data minusState it) }
            .also { lastResult = it }

    private companion object {
        // 里程计线性可加性（用于加权平均）
        operator fun Odometry.times(k: Double) =
            Odometry(p * k, d * k)

        // 里程计线性可数乘（用于加权平均）
        operator fun Odometry.plus(other: Odometry) =
            Odometry(p + other.p, d rotate other.d)

        // 限幅算法
        infix fun <T : Comparable<T>> T.clamp(range: ClosedRange<T>) =
            when {
                this < range.start        -> range.start
                this > range.endInclusive -> range.endInclusive
                else                      -> this
            }
    }
}
