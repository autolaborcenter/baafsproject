package cn.autolabor.locator

import cn.autolabor.amcl.AMCLFilterBuilderDsl
import cn.autolabor.amcl.AMCLFusionModuleBuilderDsl.Companion.startLocationFusion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.mechdancer.algebra.function.vector.minus
import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.algebra.function.vector.plus
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOfZero
import org.mechdancer.annotations.BuilderDslMarker
import org.mechdancer.channel
import org.mechdancer.common.Stamped
import org.mechdancer.common.filters.Differential
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.geometry.transformation.Pose2D
import org.mechdancer.geometry.transformation.minusState
import org.mechdancer.geometry.transformation.pose2D
import org.mechdancer.geometry.transformation.toPose2D
import org.mechdancer.newRandomDriving
import org.mechdancer.paintPose
import org.mechdancer.remote.presets.RemoteHub
import org.mechdancer.simulation.*
import org.mechdancer.simulation.DifferentialOdometry.Key
import org.mechdancer.simulation.random.Normal
import org.mechdancer.struct.StructBuilderDSL
import java.util.*
import kotlin.random.Random

/** 粒子滤波测试用例构建 */
@BuilderDslMarker
class AMCLFilterDebugerBuilderDsl private constructor() {
    // 仿真配置
    // 倍速仿真
    var speed = 1
    // 仿真器工作频率
    var frequency = 50L
    // 机器人起始位姿
    var origin = pose2D()
    // 定位配置
    // 定位频率
    var beaconFrequency = 7.0
    // 丢包率
    var beaconLossRate = .05
    // 定位噪声标准差
    var beaconSigma = 1E-3
    // 定位平均延时
    var beaconDelay = 170L
    // 定位标签位置
    var beaconOnRobot = vector2DOfZero()

    // 定位异常配置
    @BuilderDslMarker
    data class BeaconErrorSourceBuilderDsl
    internal constructor(
        var pStart: Double = .0,
        var pStop: Double = 1.0,
        var range: Double = .0)

    @BuilderDslMarker
    class BeaconErrorSourcesBuilderDsl internal constructor() {
        internal var beaconErrors = mutableListOf<AccidentalBeaconErrorSource>()

        fun error(block: BeaconErrorSourceBuilderDsl.() -> Unit) {
            BeaconErrorSourceBuilderDsl()
                .apply(block)
                .apply {
                    require(pStart in .0..1.0)
                    require(pStop in .0..1.0)
                    require(range >= 0)
                }
                .takeIf { it.pStart > 0 && it.range > 0 }
                ?.apply {
                    this@BeaconErrorSourcesBuilderDsl
                        .beaconErrors
                        .add(AccidentalBeaconErrorSource(
                            pStart = pStart,
                            pStop = pStop,
                            range = range))
                }
        }
    }

    private var errors = BeaconErrorSourcesBuilderDsl()
    fun beaconErrors(block: BeaconErrorSourcesBuilderDsl.() -> Unit) {
        errors.apply(block)
    }

    // 里程计配置
    var odometryFrequency = 20.0
    var leftWheel = Vector2D(.0, +.2)
    var rightWheel = Vector2D(.0, -.2)
    var wheelsWidthMeasure = 0.4
    // 滤波器配置
    private var filterConfig: AMCLFilterBuilderDsl.() -> Unit = {}

    fun AMCLFilter(block: AMCLFilterBuilderDsl.() -> Unit) {
        filterConfig = block
    }

    // 数据分析方法
    private var analyzer: (t: Long, actual: Pose2D, odometry: Pose2D) -> Unit =
        { t, actual, odometry ->
            displayOnConsole(
                "时间" to t / 1000.0,
                "误差" to (actual.p - odometry.p).norm())
        }

    fun analyze(block: (Long, Pose2D, Pose2D) -> Unit) {
        analyzer = block
    }

    // 绘图
    var painter: RemoteHub? = null

    companion object {
        private const val T0 = 0L
        private const val BEACON_TAG = "定位标签"

        @ExperimentalCoroutinesApi
        fun debugAMCLFilter(block: AMCLFilterDebugerBuilderDsl.() -> Unit = {}) {
            AMCLFilterDebugerBuilderDsl()
                .apply(block)
                .apply {
                    require(frequency > 0)
                    require(beaconFrequency > 0)
                    require(odometryFrequency > 0)
                    require(beaconLossRate in 0.0..1.0)
                }
                .run {
                    // 机器人机械结构
                    val robot = StructBuilderDSL.struct(Chassis(Stamped(T0, origin))) {
                        Encoder(Key.Left) asSub { pose = Pose2D(leftWheel, 0.toRad()) }
                        Encoder(Key.Right) asSub { pose = Pose2D(rightWheel, 0.toRad()) }
                        BEACON_TAG asSub { pose = Pose2D(beaconOnRobot, 0.toRad()) }
                    }
                    // 编码器在机器人上的位姿
                    val encodersOnRobot =
                        robot.devices
                            .mapNotNull { (device, tf) -> (device as? Encoder)?.to(tf.toPose2D()) }
                            .toMap()
                    // 定位标签在机器人上的位姿
                    val beaconOnRobot =
                        robot.devices[BEACON_TAG]!!.toPose2D().p

                    // 标签延时队列
                    val beaconQueue: Queue<Stamped<Vector2D>> = LinkedList<Stamped<Vector2D>>()
                    // 标签偏置
                    val beaconErrors = errors.beaconErrors
                    // 里程计周期
                    val beaconPeriod = 1000L / beaconFrequency
                    // 定位标签采样计数
                    var beaconTimes = 0L

                    // 差动里程计
                    val odometry = DifferentialOdometry(wheelsWidthMeasure, Stamped(T0, pose2D()))
                    // 里程计周期
                    val odometryPeriod = 1000L / odometryFrequency
                    // 里程计采样计数
                    var odometryTimes = 0L

                    // 随机行驶控制器
                    val random = newRandomDriving().let { if (speed > 0) it power speed else it }
                    // 位姿增量计算
                    val differential = Differential(robot.what.get(), T0) { _, old, new -> new minusState old }
                    // 机器人位姿缓存
                    var actual = robot.what.odometry

                    // 话题
                    val robotOnOdometry = channel<Stamped<Pose2D>>()
                    val beaconOnMap = channel<Stamped<Vector2D>>()
                    val robotOnMap = channel<Stamped<Pose2D>>()
                    runBlocking {
                        // 任务
                        startLocationFusion(
                            robotOnOdometry = robotOnOdometry,
                            beaconOnMap = beaconOnMap,
                            robotOnMap = robotOnMap
                        ) {
                            filter(this@run.filterConfig)
                            painter = this@run.painter
                        }
                        launch {
                            // 在控制台打印误差
                            for ((t, pose) in robotOnMap)
                                if (t == actual.time) analyzer(t, actual.data, pose)
                        }
                        // 运行仿真 //random.next()
                        for ((t, v) in speedSimulation(T0, 1000L / frequency, speed) { random.next() }) {
                            //  计算机器人位姿增量
                            actual = robot.what.drive(v, t)
                            val delta = differential.update(actual.data, t).data
                            // 计算编码器增量
                            for ((encoder, p) in encodersOnRobot) encoder.update(p, delta)
                            // 计算里程计
                            val get = { key: Key -> encodersOnRobot.keys.single { (k, _) -> k == key }.value }
                            val pose = odometry.update(get(Key.Left) to get(Key.Right), t).data
                            // 定位采样
                            if (t > beaconTimes * beaconPeriod) {
                                ++beaconTimes
                                if (Random.nextDouble() > beaconLossRate)
                                    beaconQueue += beaconErrors
                                        .fold(Vector2D(
                                            Normal.next(.0, beaconSigma),
                                            Normal.next(.0, beaconSigma))
                                        ) { sum, source -> sum + source.next() }
                                        .let {
                                            Stamped(t, actual.data * beaconOnRobot + it)
                                        }
                            }
                            // 延时发送采样
                            while (beaconQueue.peek()?.time?.let { it < t - beaconDelay } == true)
                                beaconOnMap.send(beaconQueue.poll()!!)
                            // 里程计采样
                            if (t > odometryTimes * odometryPeriod) {
                                ++odometryTimes
                                robotOnOdometry.send(Stamped(t, pose))
                            }
                            // 显示
                            painter?.paintPose("实际", actual.data)
                        }
                    }
                }
        }
    }
}
