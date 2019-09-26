package cn.autolabor.locator

import cn.autolabor.utilities.ClampMatcher
import org.mechdancer.Temporary
import org.mechdancer.Temporary.Operation.DELETE
import org.mechdancer.Temporary.Operation.REDUCE
import org.mechdancer.algebra.function.vector.*
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.algebra.implement.vector.vector2DOfZero
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.toTransformation
import org.mechdancer.geometry.angle.*
import org.mechdancer.geometry.transformation.Transformation
import org.mechdancer.simulation.random.Normal
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min

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
class ParticleFilter(private val count: Int,
                     private val locatorOnRobot: Vector2D,
                     private val locatorWeight: Double,
                     private val maxInterval: Long,
                     private val maxInconsistency: Double,
                     private val maxAge: Int,
                     private val sigma: Double
) : Mixer<Stamped<Odometry>, Stamped<Vector2D>, Stamped<Odometry>> {
    private val matcher = ClampMatcher<Stamped<Odometry>, Stamped<Vector2D>>()

    // 过程记录器
    @Temporary(DELETE)
    val stepFeedback = mutableListOf<(StepState) -> Unit>()

    // 粒子：位姿 - 寿命
    @Temporary(REDUCE)
    var particles = emptyList<Pair<Odometry, Int>>()

    // 过程参数渗透
    @Temporary(DELETE)
    data class StepState(val measureWeight: Double,
                         val particleWeight: Double,
                         val measure: Vector2D,
                         val state: Odometry,
                         val locatorExpectation: Odometry,
                         val robotExpectation: Odometry)

    override fun measureMaster(item: Stamped<Odometry>) =
        matcher.add1(item).let {
            update()
            get(item)
        }

    override fun measureHelper(item: Stamped<Vector2D>) =
        matcher.add2(item).also {
            update()
        }

    private var stateSave: Pair<Vector2D, Odometry>? = null
    private var expectation = Odometry()

    private fun update() {
        synchronized(particles) {
            generateSequence(matcher::match2)
                // 匹配 ↑
                // 插值 ↓
                .mapNotNull { (measure, before, after) ->
                    (after.time - before.time)
                        // 一对匹配项间隔不应该超过间隔范围
                        .takeIf { it in 1..maxInterval }
                        ?.let { interval ->
                            val k = (measure.time - before.time).toDouble() / interval
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
                    // 从原始测量量计算增量
                    val deltaState = state minusState lastState
                    val deltaMeasure = measure - lastMeasure
                    stateSave = measure to state
                    // 计算不一致性，若过于不一致则放弃更新
                    val lengthM = deltaMeasure.norm()
                    val lengthS = (deltaState.toTransformation()(locatorOnRobot) - locatorOnRobot).norm()
                    val inconsistency = abs(lengthM - lengthS).takeIf { it < maxInconsistency } ?: return@forEach
                    // 计算校准权重：定位器本身的可靠性与此次测量的可靠性相乘
                    val measureWeight = locatorWeight * mapOf(
                        // 校准值变化大本身就意味着不可靠
                        1 to 1 - min(1.0, lengthM / maxInconsistency),
                        // 校准值与测量值越不一致，意味着校准值越不可靠
                        2 to 1 - min(1.0, inconsistency / maxInconsistency)
                    ).run { toList().sumByDouble { (k, value) -> k * value } / keys.sum() }
                    // 更新粒子群
                    particles =
                        particles.map { (p, age) -> (p plusDelta deltaState) to min(age + 1, maxAge) }
                    // 计算每个粒子对应的校准器坐标
                    val locators =
                        particles.map { (p, age) -> Odometry(p.toTransformation()(locatorOnRobot).to2D(), p.d) to age }
                    // 计算粒子权重
                    val weights = locators
                        .map { (odometry, age) ->
                            age.ageWeight() * (1 - min(1.0, (odometry.p - measure).norm() / maxInconsistency))
                        }
                    // 计算粒子总权重，若过低，直接重新初始化
                    val weightsSum = weights.sum().takeIf { it > 0.1 * count }
                                     ?: run {
                                         initialize(measure, state)
                                         return@forEach
                                     }
                    // 计算期望和方差
                    var eP = vector2DOfZero()
                    var eD = vector2DOfZero()
                    locators.forEachIndexed { i, (odometry, _) ->
                        val (p, d) = odometry
                        val k = weights[i]
                        eP += p * k
                        eD += d.toVector() * k
                    }
                    eP = (eP + measure * measureWeight) / (weightsSum + measureWeight)
                    eD /= weightsSum
                    val eAngle = eD.toAngle()
                    val angle = eAngle.asRadian()
                    // 计算方向标准差，对偏差较大的粒子进行随机方向的重采样
                    particles = particles.mapIndexed { i, item ->
                        val age = item.second
                        if ((locators[i].first.p - measure).norm() > 0.05) {
                            val newAge = item.second - 2
                            if (newAge < 0) {
                                val d = Normal.next(angle, sigma).toRad()
                                val p = Transformation.fromPose(measure, d)(-locatorOnRobot).to2D()
                                Odometry(p, d) to 0
                            } else item.copy(second = newAge)
                        } else item
                    }
                    // 猜测真实位姿
                    val eRobot = Transformation.fromPose(eP, eAngle)(-locatorOnRobot).to2D()
                    expectation = Odometry(eRobot, eAngle)
                    @Temporary(DELETE)
                    val stepState = StepState(measureWeight, weightsSum,
                                              measure, state,
                                              Odometry(eP, eAngle),
                                              Odometry(eRobot, eAngle))
                    synchronized(stepFeedback) {
                        for (callback in stepFeedback) callback(stepState)
                    }
                }
        }
    }

    // 来自寿命的权重增益
    private fun Int.ageWeight() = (maxAge + this).toDouble() / (2 * maxAge)

    // 重新初始化
    private fun initialize(measure: Vector2D, state: Odometry) {
        stateSave = measure to state
        val step = 2 * PI / count
        particles = List(count) {
            val d = (it * step).toRad()
            val p = Transformation.fromPose(measure, d)(-locatorOnRobot).to2D()
            Odometry(p, d) to 0
        }
    }

    override operator fun get(item: Stamped<Odometry>) =
        stateSave?.second
            ?.let { Stamped(item.time, expectation plusDelta (item.data minusState it)) }

    private companion object {
        // 里程计线性可加性（用于加权平均）
        operator fun Odometry.times(k: Double) =
            Odometry(p * k, d * k)

        // 里程计线性可数乘（用于加权平均）
        operator fun Odometry.plus(other: Odometry) =
            Odometry(p + other.p, d rotate other.d)
    }
}
