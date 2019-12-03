package cn.autolabor.pm1

import cn.autolabor.autocan.AutoCANPackageHead
import cn.autolabor.autocan.PM1Pack
import cn.autolabor.autocan.engine
import cn.autolabor.pm1.model.*
import cn.autolabor.pm1.model.ControlVariable.Physical
import cn.autolabor.serialport.manager.Certificator
import cn.autolabor.serialport.manager.OpenCondition
import cn.autolabor.serialport.manager.SerialPortDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mechdancer.ClampMatcher
import org.mechdancer.SimpleLogger
import org.mechdancer.WatchDog
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.core.Chassis
import org.mechdancer.geometry.angle.Angle
import org.mechdancer.geometry.angle.toDegree
import org.mechdancer.geometry.angle.toRad
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.max

/**
 * 机器人底盘
 */
class SerialPortChassis internal constructor(
    scope: CoroutineScope,
    private val robotOnOdometry: SendChannel<Stamped<Odometry>>,

    wheelEncodersPulsesPerRound: Int,
    rudderEncoderPulsesPerRound: Int,

    width: Double,
    leftRadius: Double,
    rightRadius: Double,
    length: Double,

    private val odometryInterval: Long,
    maxWheelSpeed: Angle,
    maxV: Double,
    maxW: Angle,
    optimizeWidth: Angle,
    maxAccelerate: Double
) : Chassis<ControlVariable>,
    SerialPortDevice,
    CoroutineScope by scope {

    override val tag = "PM1 Chassis"
    override val openCondition = OpenCondition.None
    override val baudRate = 115200
    override val bufferSize = 64
    override val retryInterval = 100L

    private val _toDevice = Channel<ByteArray>()
    private val _toDriver = Channel<Iterable<Byte>>()

    override val toDevice get() = _toDevice
    override val toDriver get() = _toDriver

    // 解析引擎
    private val engine = engine()
    // 无状态计算模型
    private val controlPeriod = odometryInterval / 2
    private val wheelsEncoder = IncrementalEncoder(wheelEncodersPulsesPerRound)
    private val rudderEncoder = IncrementalEncoder(rudderEncoderPulsesPerRound)
    private val structure = ChassisStructure(width, leftRadius, rightRadius, length)
    private val optimizer = Optimizer(structure, maxWheelSpeed, maxV, maxW, optimizeWidth, maxAccelerate, controlPeriod)
    private val predictor = Predictor(structure, optimizer, controlPeriod, 30.toDegree())
    // 里程计
    private val wheelsEncoderMatcher = ClampMatcher<Stamped<Int>, Stamped<Int>>(false)
    // 节点状态
    private val ecuL = CanNode.ECU(0)
    private val ecuR = CanNode.ECU(1)
    private val tcu = CanNode.TCU(0)
    private val vcu = CanNode.VCU(0)
    // 内部状态
    private val controlWatchDog =
        WatchDog(this, 10 * odometryInterval)
        { enabled = false }
    private var lastPhysical = Physical.static

    var enabled = false
        set(value) {
            if (!value) lastPhysical = lastPhysical.copy(speed = .0)
            field = value
        }

    override var odometry = Stamped(0L, Odometry.pose())
        private set

    override var target: ControlVariable =
        Physical.static
        set(value) {
            enabled = true
            controlWatchDog.feed()
            field = value
        }
    // 日志
    private val logger = SimpleLogger("Chassis")
    private val commandsLogger = SimpleLogger("ChassisCommands")

    // 轨迹预测
    override fun predict(target: ControlVariable) =
        predictor.predict(target, lastPhysical)

    override fun buildCertificator() =
        object : Certificator {
            override val activeBytes =
                sequenceOf(CanNode.ECU().currentPositionTx,
                           CanNode.TCU(0).currentPositionTx,
                           CanNode.VCU(0).batteryPercentTx)
                    .map(AutoCANPackageHead.WithoutData::pack)
                    .map(ByteArray::asList)
                    .flatten()
                    .toList()
                    .toByteArray()

            private val t0 = System.currentTimeMillis()
            private var left = false
            private var right = false
            private var rudder = false
            private var battery = false
            override fun invoke(bytes: Iterable<Byte>): Boolean? {
                engine(bytes) { pack ->
                    if (pack !is PM1Pack.WithData) return@engine
                    val now = System.currentTimeMillis()
                    when (pack.head) {
                        ecuL.currentPositionRx -> {
                            val pulse = pack.getInt()
                            ecuL.position = Stamped(now, pulse.let(wheelsEncoder::toAngular))
                            wheelsEncoderMatcher.add1(Stamped(now, pulse))
                            left = true
                        }
                        ecuR.currentPositionRx -> {
                            val pulse = pack.getInt()
                            ecuR.position = Stamped(now, pulse.let(wheelsEncoder::toAngular))
                            wheelsEncoderMatcher.add2(Stamped(now, pulse))
                            right = true
                        }
                        tcu.currentPositionRx  -> {
                            val pulse = pack.getShort()
                            tcu.position = Stamped(now, pulse.let(rudderEncoder::toAngular))
                            rudder = true
                        }
                        vcu.batteryPercentRx   -> {
                            vcu.batteryPercent = pack.data[0]
                            battery = true
                        }
                    }
                }
                return when {
                    System.currentTimeMillis() - t0 > 1000L -> false
                    left && right && rudder && battery      -> true
                    else                                    -> null
                }
            }
        }

    init {
        // 启动轮转发送线程
        scope.launch {
            val msg =
                listOf(CanNode.EveryNode.stateTx to 1000L,
                       CanNode.ECU().currentPositionTx to odometryInterval,
                       tcu.currentPositionTx to odometryInterval / 2
                ).map { (head, t) -> head.pack().asList() to t }
            val flags =
                System.currentTimeMillis().let {
                    LongArray(msg.size) { i -> it + msg[i].second }
                }
            while (true) {
                val now = System.currentTimeMillis()
                msg.indices
                    .flatMap { i ->
                        if (flags[i] < now) {
                            flags[i] += msg[i].second
                            msg[i].first
                        } else emptyList()
                    }
                    .toByteArray()
                    .also {
                        _toDevice.send(it)
                        logger.log("${it.size} bytes sent")
                    }
                delay(max(1, flags.min()!! - now + 1))
            }
        }
        scope.launch {
            val serial = mutableListOf<Byte>()
            for (bytes in toDriver) {
                serial.clear()
                engine(bytes) { pack ->
                    val now = System.currentTimeMillis()
                    when (pack) {
                        PM1Pack.Nothing,
                        PM1Pack.Failed,
                        is PM1Pack.WithoutData -> Unit
                        is PM1Pack.WithData    ->
                            when (pack.head) {
                                // 左轮状态
                                ecuL.stateRx           -> {
                                    ecuL.state = Stamped(now, pack.getState())
                                    logger.log("left ecu state received")
                                }
                                // 右轮状态
                                ecuR.stateRx           -> {
                                    ecuR.state = Stamped(now, pack.getState())
                                    logger.log("right ecu state received")
                                }
                                // 舵轮状态
                                tcu.stateRx            -> {
                                    tcu.state = Stamped(now, pack.getState())
                                    logger.log("tcu state received")
                                }
                                // 整车控制器状态
                                vcu.stateRx            -> {
                                    vcu.state = Stamped(now, pack.getState())
                                    logger.log("vcu state received")
                                }
                                // 左轮编码器
                                ecuL.currentPositionRx -> {
                                    wheelsEncoderMatcher.add1(Stamped(now, pack.getInt()))
                                    val (t, data) =
                                        wheelsEncoderMatcher.match2()
                                            ?.takeIf(::checkInterval)
                                            ?.takeIf { (new, _, _) -> new > ecuR.position }
                                            ?.let(::interpolateMatcher)
                                        ?: return@engine
                                    val (r, l) = data
                                    updateOdometry(t = t,
                                                   l = l.let(wheelsEncoder::toAngular),
                                                   r = r.let(wheelsEncoder::toAngular))
                                    logger.log("left encoder received")
                                }
                                // 右轮编码器
                                ecuR.currentPositionRx -> {
                                    wheelsEncoderMatcher.add2(Stamped(now, pack.getInt()))
                                    val (t, data) =
                                        wheelsEncoderMatcher.match1()
                                            ?.takeIf(::checkInterval)
                                            ?.takeIf { (new, _, _) -> new > ecuL.position }
                                            ?.let(::interpolateMatcher)
                                        ?: return@engine
                                    val (l, r) = data
                                    updateOdometry(t = t,
                                                   l = l.let(wheelsEncoder::toAngular),
                                                   r = r.let(wheelsEncoder::toAngular))
                                    logger.log("right encoder received")
                                }
                                // 舵轮编码器
                                tcu.currentPositionRx  -> {
                                    val rudder = pack.getShort().let(rudderEncoder::toAngular)
                                    tcu.position = Stamped(now, rudder)
                                    logger.log("rudder encoder received")
                                    if (enabled) {
                                        // 优化控制量
                                        val (speed, l, r, t) = optimizer(target, lastPhysical.copy(rudder = rudder))
                                        lastPhysical = Physical(speed, rudder)
                                        // 生成脉冲数
                                        val pl = l.let(wheelsEncoder::toPulses)
                                        val pr = r.let(wheelsEncoder::toPulses)
                                        val pt = t?.let(rudderEncoder::toPulses)?.toShort()
                                        // 准备发送
                                        serial.addAll(ecuL.targetSpeed.pack(pl).asList())
                                        serial.addAll(ecuR.targetSpeed.pack(pr).asList())
                                        if (pt != null)
                                            serial.addAll(tcu.targetPosition.pack(pt).asList())
                                        commandsLogger.log(l, r, t)
                                    }
                                }
                            }
                    }
                }
                toDevice.send(serial.toByteArray())
            }
        }
    }

    // 检查两轮数据时间差
    private fun checkInterval(
        matcher: Triple<Stamped<Int>, Stamped<Int>, Stamped<Int>>
    ): Boolean {
        val (_, before, after) = matcher
        return after.time in before.time..before.time + 2 * odometryInterval
    }

    // 对编码器做插值匹配
    private fun interpolateMatcher(
        matcher: Triple<Stamped<Int>, Stamped<Int>, Stamped<Int>>
    ): Stamped<Pair<Number, Number>>? {
        val (new, before, after) = matcher
        if (after.time - before.time < 3) return null // TODO 为什么？？？
        val k = (after.time - new.time).toDouble() / (after.time - before.time)
        val interpolation = before.data * k + after.data * (1 - k)
        return Stamped(new.time, new.data to interpolation)
    }

    // 更新里程计
    private fun updateOdometry(t: Long, l: Angle, r: Angle) {
        val `ln-1` = ecuL.position.data.asRadian()
        val `rn-1` = ecuR.position.data.asRadian()
        ecuL.position = Stamped(t, l)
        ecuR.position = Stamped(t, r)
        val delta = structure.toDeltaOdometry(
            (l.asRadian() - `ln-1`).toRad(),
            (r.asRadian() - `rn-1`).toRad())
        odometry = Stamped(t, odometry.data plusDelta delta)
        launch { robotOnOdometry.send(odometry) }
    }

    private companion object {
        fun PM1Pack.WithData.getState() =
            when (data[0]) {
                0x01.toByte() -> CanNode.State.Normal
                0xff.toByte() -> CanNode.State.Lock
                else          -> CanNode.State.Unknown
            }

        fun PM1Pack.WithData.getInt() =
            data.copyOfRange(0, Int.SIZE_BYTES)
                .let(::ByteArrayInputStream)
                .let(::DataInputStream)
                .readInt()

        fun PM1Pack.WithData.getShort() =
            data.copyOfRange(0, Short.SIZE_BYTES)
                .let(::ByteArrayInputStream)
                .let(::DataInputStream)
                .readShort()

        fun AutoCANPackageHead.WithData.pack(short: Short) =
            ByteArrayOutputStream(Short.SIZE_BYTES)
                .also { DataOutputStream(it).writeShort(short.toInt()) }
                .toByteArray()
                .let { this.pack(data = it) }

        fun AutoCANPackageHead.WithData.pack(int: Int) =
            ByteArrayOutputStream(Int.SIZE_BYTES)
                .also { DataOutputStream(it).writeInt(int) }
                .toByteArray()
                .let { this.pack(data = it) }
    }
}
