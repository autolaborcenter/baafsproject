package cn.autolabor.pm1

import cn.autolabor.autocan.AutoCANPackageHead
import cn.autolabor.autocan.PM1Pack
import cn.autolabor.autocan.engine
import cn.autolabor.pm1.model.*
import cn.autolabor.pm1.model.ControlVariable.Physical
import cn.autolabor.serialport.manager.Certificator
import cn.autolabor.serialport.manager.SerialPortDeviceBase
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.mechdancer.ClampMatcher
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
    private val robotOnOdometry: SendChannel<Stamped<Odometry>>,

    portName: String?,

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
) : SerialPortDeviceBase("PM1 chassis", 115200, 64, portName),
    Chassis<ControlVariable> {
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
        WatchDog(GlobalScope, 10 * odometryInterval)
        { enabled = false }
    private var lastPhysical =
        Physical.static
    private val externalCommands =
        Channel<ByteArray>(UNLIMITED)

    /** 驱动使能 */
    var enabled = false
        set(value) {
            if (!value) lastPhysical = lastPhysical.copy(speed = .0)
            field = value
        }

    /** 电池电量 */
    val battery get() = vcu.batteryPercent

    override var odometry = Stamped(0L, Odometry.pose())
        private set

    override var target: ControlVariable = Physical.static
        set(value) {
            enabled = true
            controlWatchDog.feed()
            if (enabled)
                field = value
        }

    fun unLockJava() {
        runBlocking { unLock() }
    }

    // 轨迹预测
    override fun predict(target: ControlVariable) =
        predictor.predict(target, lastPhysical)

    suspend fun unLock() {
        externalCommands.send(releaseStop)
    }

    override fun buildCertificator(): Certificator =
        object : CertificatorBase(1000L) {
            override val activeBytes =
                sequenceOf(
                    CanNode.ECU().currentPositionTx,
                    CanNode.TCU(0).currentPositionTx,
                    CanNode.VCU(0).batteryPercentTx
                )
                    .map(AutoCANPackageHead.WithoutData::pack)
                    .map(ByteArray::asList)
                    .flatten()
                    .toList()
                    .toByteArray()

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
                return passOrTimeout(left && right && rudder && battery)
            }
        }

    override fun setup(
        scope: CoroutineScope,
        toDevice: SendChannel<List<Byte>>,
        fromDevice: ReceiveChannel<List<Byte>>
    ) {
        scope.launch {
            val msg =
                listOf(
                    CanNode.EveryNode.stateTx to 1000L,
                    CanNode.ECU().currentPositionTx to odometryInterval,
                    tcu.currentPositionTx to odometryInterval / 2,
                    vcu.batteryCurrentTx to 5000L
                ).map { (head, t) -> head.pack().asList() to t }
            val flags =
                System.currentTimeMillis().let {
                    LongArray(msg.size) { i -> it + msg[i].second }
                }
            while (isActive) {
                val now = System.currentTimeMillis()
                msg.indices
                    .flatMap { i ->
                        if (flags[i] < now) {
                            flags[i] += msg[i].second
                            msg[i].first
                        } else emptyList()
                    }
                    .toList()
                    .also { toDevice.send(it) }
                delay(max(1, flags.minOrNull()!! - now + 1))
            }
        }
        scope.launch {
            for (pack in externalCommands)
                toDevice.send(pack.asList())
        }
        scope.launch {
            val serial = mutableListOf<Byte>()
            for (bytes in fromDevice) {
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
                                ecuL.stateRx           ->
                                    ecuL.state = Stamped(now, pack.getState())
                                // 右轮状态
                                ecuR.stateRx           ->
                                    ecuR.state = Stamped(now, pack.getState())
                                // 舵轮状态
                                tcu.stateRx            ->
                                    tcu.state = Stamped(now, pack.getState())
                                // 整车控制器状态
                                vcu.stateRx            ->
                                    vcu.state = Stamped(now, pack.getState())
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
                                    updateOdometry(
                                        t = t,
                                        l = l.let(wheelsEncoder::toAngular),
                                        r = r.let(wheelsEncoder::toAngular)
                                    )
                                    scope.launch { robotOnOdometry.send(odometry) }
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
                                    updateOdometry(
                                        t = t,
                                        l = l.let(wheelsEncoder::toAngular),
                                        r = r.let(wheelsEncoder::toAngular)
                                    )
                                    scope.launch { robotOnOdometry.send(odometry) }
                                }
                                // 舵轮编码器
                                tcu.currentPositionRx  -> {
                                    val rudder = pack.getShort().let(rudderEncoder::toAngular)
                                    tcu.position = Stamped(now, rudder)
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
                                    }
                                }
                            }
                    }
                }
                toDevice.send(serial.toList())
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
            (r.asRadian() - `rn-1`).toRad()
        )
        odometry = Stamped(t, odometry.data plusDelta delta)
    }

    private companion object {
        val releaseStop = CanNode.EveryNode.releaseStop.pack(data = byteArrayOf(0xff.toByte()))

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
