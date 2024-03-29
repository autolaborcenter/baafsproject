package cn.autolabor.locator

import org.mechdancer.ClampMatcher
import org.mechdancer.Schmitt
import org.mechdancer.algebra.function.vector.*
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.algebra.implement.vector.vector2DOfZero
import org.mechdancer.annotations.DebugTemporary
import org.mechdancer.annotations.DebugTemporary.Operation.DELETE
import org.mechdancer.average
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.toTransformation
import org.mechdancer.geometry.angle.Angle
import org.mechdancer.geometry.angle.toAngle
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.geometry.angle.toVector
import org.mechdancer.geometry.transformation.Transformation
import org.mechdancer.simulation.random.Normal
import kotlin.math.*

/**
 * 粒子滤波器
 *
 * * 参数
 *   * 使用固定 [count] 个粒子的粒子滤波器
 *   * 机器人坐标系的 [locatorOnRobot] 处存在一个定位校准器，其可产生 [Vector2D] 表示的位置信号
 *   * 根据定位校准器本身的精度，在计算时，将其视为 [locatorWeight] 个粒子
 *   * 测量信号与校准信号用时间戳夹逼匹配，最大匹配间隔为 [maxInterval] 毫秒，并在夹逼间线性插值
 *   * 最大不一致性 [maxInconsistency] 米
 *     测量传感器与校准传感器具有不同的误差模型，但可以确定一点：对同一个量的测量，二者不会相差太大
 *     若两次匹配之间两种传感器增量的欧氏长度相差大于这个值，则不使用新的匹配对更新测量
 *   * 运行中对粒子寿命的统计最大取值为 [maxAge]
 *   * 对死亡的粒子重采样时方向按标准差 [sigma] 进行随机
 */
class ParticleFilter(
    private val count: Int,
    private val locatorOnRobot: Vector2D,
    private val locatorWeight: Double,
    private val maxInterval: Long,
    private val maxInconsistency: Double,
    private val maxAge: Int,
    private val sigma: Double,
    private val predicate: Schmitt<FusionQuality>
) : Mixer<Stamped<Odometry>, Stamped<Vector2D>, Stamped<Odometry>> {
    // 无状态计算模型
    private val gauss = Gauss(.0, maxInconsistency / 3)

    // 匹配器
    private val matcher = ClampMatcher<Stamped<Odometry>, Stamped<Vector2D>>(true)

    // 匹配步进状态
    private var stepMemory: Pair<Vector2D, Odometry>? = null

    // 融合步进状态
    private lateinit var updatingMemory: Odometry

    // 粒子群：位姿 - 寿命
    private var particles = emptyList<Pair<Odometry, Int>>()

    // 预测器
    private var visionary = Visionary(Odometry.pose(), Odometry.pose(), .0)

    // 过程记录器
    @DebugTemporary(DELETE)
    val stepFeedbacks = mutableListOf<(Stamped<StepState>) -> Unit>()

    // 质量状态
    var quality = Stamped(0L, FusionQuality.zero)
        private set

    // 是否收敛
    val isConvergent get() = predicate.state

    // 最后一次查询结果
    var lastQuery: Stamped<Odometry>? = null
        private set

    // 过程参数渗透
    @DebugTemporary(DELETE)
    data class StepState(
        val measureWeight: Double,
        val particleWeight: Double,
        val quality: FusionQuality
    )

    override fun measureMaster(item: Stamped<Odometry>) =
        matcher.add1(item).let {
            update()
            get(item)
        }

    override fun measureHelper(item: Stamped<Vector2D>) =
        matcher.add2(item).also {
            update()
        }

    override operator fun get(item: Stamped<Odometry>): Stamped<Odometry> {
        val (t, p) = item
        return Stamped(t, visionary.infer(p)).also { lastQuery = it }
    }

    fun getOrSet(item: Stamped<Odometry>, target: Odometry): Stamped<Odometry> {
        val (_, p) = item
        if (!isConvergent) updateVisionary(p, target)
        return get(item)
    }

    // 更新预测器
    private fun updateVisionary(
        newMarkOnOdometry: Odometry,
        newExpectation: Odometry
    ) {
        visionary = visionary.fusion(
            newMarkOnOdometry,
            newExpectation,
            2.0,
            maxInconsistency
        )
    }

    @Synchronized
    private fun update() {
        generateSequence(matcher::match2)
            // 匹配 ↑
            // 插值 ↓
            .mapNotNull { (measure, before, after) ->
                val (t, m) = measure
                (after.time - before.time)
                    // 一对匹配项间隔不应该超过间隔范围
                    .takeIf { it in 1..maxInterval }
                    ?.let { interval ->
                        val k = (t - before.time).toDouble() / interval
                        Stamped(t, m to average(before.data to (1 - k), after.data to k))
                    }
            }
            // 计算定位权重
            .mapNotNull { stamped ->
                val (t, pair) = stamped
                val (measure, state) = pair
                val last = stepMemory
                stepMemory = pair
                // 判断第一帧
                if (last == null) {
                    initialize(t, measure, state)
                    null
                } else {
                    val (lastMeasure, lastState) = last
                    val deltaState = state minusState lastState
                    val lengthM = measure euclid lastMeasure
                    val lengthS = deltaState.toTransformation()(locatorOnRobot) euclid locatorOnRobot
                    // 计算不一致性
                    abs(lengthM - lengthS)
                        .takeIf { it < maxInconsistency }
                        ?.let { stamped to locatorWeight * gauss.p(it) }
                }
            }
            // 计算
            .forEach { (stamped, measureWeight) ->
                val (t, pair) = stamped
                val (measure, state) = pair
                val delta = state minusState updatingMemory
                updatingMemory = state
                // 更新粒子群
                particles = particles.map { (p, age) -> (p plusDelta delta) to age }
                // 计算每个粒子对应的信标坐标
                val limitedMaxAge =
                    max(3, (maxAge * particles.qualityBy(maxAge, maxInconsistency).direction).roundToInt())
                val beacons = particles.map { (p, _) -> Odometry(p.toTransformation()(locatorOnRobot).to2D(), p.d) }
                val distances = beacons.map { (p, _) -> p euclid measure }
                val ages = particles.zip(distances) { (_, age), distance ->
                    if (distance < maxInconsistency) min(limitedMaxAge, age + 1) else age - 1
                }
                // 计算粒子权重
                val weights = ages.zip(distances) { age, distance ->
                    age.toDouble() / maxAge * gauss.p(distance)
                }
                // 计算粒子总权重，若过低，直接重新初始化
                val weightsSum = weights.sum()
                    .takeIf { it > 1 }
                    ?: run {
                        // 写入当前寿命
                        particles = particles.zip(ages) { (p, _), age -> p to max(age, 0) }
                        // 计算定位质量
                        quality = Stamped(t, particles.qualityBy(maxAge, maxInconsistency))
                        // 重新初始化
                        if (!predicate.update(quality.data)) initialize(t, measure, state)
                        return@forEach
                    }
                // 计算期望
                var eP = vector2DOfZero()
                var eD = vector2DOfZero()
                beacons.zip(weights) { (p, d), k ->
                    eP += p * k
                    eD += d.toVector() * k
                }
                eP = (eP + measure * measureWeight) / (weightsSum + measureWeight)
                eD /= weightsSum
                val eAngle = eD.toAngle()
                val angle = eAngle.asRadian()
                // 对偏差较大的粒子进行随机方向的重采样
                particles = particles.zip(ages) { (p, _), age ->
                    if (age < 1)
                        robotPoseBy(measure, Normal.next(angle, sigma).toRad()) to 0
                    else
                        p to age
                }
                // 计算定位质量
                quality = Stamped(t, particles.qualityBy(maxAge, maxInconsistency))
                // 猜测真实位姿
                if (predicate.update(quality.data))
                    updateVisionary(state, robotPoseBy(eP, eAngle))
                @DebugTemporary(DELETE)
                synchronized(stepFeedbacks) {
                    val msg = Stamped(
                        t, StepState(
                            measureWeight = measureWeight,
                            particleWeight = weightsSum,
                            quality = quality.data
                        )
                    )
                    for (callback in stepFeedbacks)
                        callback(msg)
                }
            }
    }

    private fun robotPoseBy(p: Vector2D, d: Angle) =
        Odometry(Transformation.fromPose(p, d)(-locatorOnRobot).to2D(), d)

    // 重新初始化
    private fun initialize(t: Long, measure: Vector2D, state: Odometry) {
        updatingMemory = state
        quality = Stamped(t, FusionQuality.zero)
        visionary = visionary.copy(reliability = .0)
        val step = 2 * PI / count
        particles = List(count) {
            val d = (it * step).toRad()
            val p = Transformation.fromPose(measure, d)(-locatorOnRobot).to2D()
            Odometry(p, d) to 0
        }
    }

    private companion object {
        fun List<Pair<Odometry, Int>>.qualityBy(
            maxAge: Int,
            maxInconsistent: Double
        ): FusionQuality {
            var ageSum = 0
            var location = vector2DOfZero()
            var direction = vector2DOfZero()
            for ((pose, age) in this) {
                val (p, d) = pose
                ageSum += age
                location += p
                direction += d.toVector()
            }
            val size = size
            val eP = location / size
            return FusionQuality(
                age = ageSum.toDouble() / (size * maxAge),
                location = max(.0, 1 - sumOf { (pose, _) -> pose.p euclid eP } / (size * maxInconsistent)),
                direction = direction.norm() / size
            )
        }
    }
}
