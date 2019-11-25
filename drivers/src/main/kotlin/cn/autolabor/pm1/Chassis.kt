package cn.autolabor.pm1

import cn.autolabor.autocan.AutoCANPackageHead
import cn.autolabor.autocan.PM1Pack
import cn.autolabor.autocan.engine
import cn.autolabor.pm1.model.ChassisStructure
import cn.autolabor.pm1.model.ControlVariable
import cn.autolabor.pm1.model.ControlVariable.*
import cn.autolabor.pm1.model.IncrementalEncoder
import cn.autolabor.serialport.parser.SerialPortFinder.Companion.findSerialPort
import cn.autolabor.serialport.parser.readOrReboot
import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.mechdancer.ClampMatcher
import org.mechdancer.SimpleLogger
import org.mechdancer.WatchDog
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.geometry.angle.Angle
import org.mechdancer.geometry.angle.toRad
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.concurrent.Executors
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.max

/**
 * 机器人底盘
 */
class Chassis(
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
    private val maxV: Double,
    maxW: Angle,
    optimizeWidth: Angle,

    private val retryInterval: Long
) : CoroutineScope by scope {
    private val wheelsEncoder = IncrementalEncoder(wheelEncodersPulsesPerRound)
    private val rudderEncoder = IncrementalEncoder(rudderEncoderPulsesPerRound)
    private val structure = ChassisStructure(width, leftRadius, rightRadius, length)

    private val maxWheelSpeedRad = maxWheelSpeed.asRadian()
    private val maxWRad = maxW.asRadian()
    private val optimizeWidthRad = optimizeWidth.asRadian()

    private val engine = engine()
    private val wheelsEncoderMatcher =
        ClampMatcher<Stamped<Int>, Stamped<Int>>(false)

    private val ecuL = CanNode.ECU(0)
    private val ecuR = CanNode.ECU(1)
    private val tcu = CanNode.TCU(0)
    private val vcu = CanNode.VCU(0)

    var odometry = Stamped(0L, Odometry.pose())
        private set

    var enabled = false

    private val controlWatchDog =
        WatchDog(this, 10 * odometryInterval)
        { enabled = false }
    var target: ControlVariable =
        Physical(.0, Double.NaN.toRad())
        set(value) {
            enabled = true
            controlWatchDog.feed()
            field = value
        }

    private val logger = SimpleLogger("Chassis")

    init {
        val candidates = SerialPort
            .getCommPorts()
            .filter {
                val name = it.systemPortName.toLowerCase()
                "com" in name || "usb" in name || "acm" in name
            }
        val port = findSerialPort(
                candidates = candidates,
                engine = engine
        ) {
            bufferSize = BUFFER_SIZE
            baudRate = 115200
            timeoutMs = 500L
            activate = activateBytes

            var left = false
            var right = false
            var rudder = false
            var battery = false
            condition { pack ->
                if (pack !is PM1Pack.WithData) return@condition false
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
                left && right && rudder && battery
            }
        }

        thread(isDaemon = true) {
            val msg =
                listOf(CanNode.EveryNode.stateTx to 1000L,
                       CanNode.ECU().currentPositionTx to odometryInterval,
                       tcu.currentPositionTx to odometryInterval / 2
                ).map { (head, t) -> head.pack() to t }
            val t0 = System.currentTimeMillis()
            val flags = LongArray(msg.size) { i -> t0 + msg[i].second }
            while (true) {
                val now = System.currentTimeMillis()
                msg.indices
                    .asSequence()
                    .filter { i -> flags[i] < now }
                    .onEach { i -> flags[i] += msg[i].second }
                    .flatMap { i -> msg[i].first.asSequence() }
                    .toList()
                    .toByteArray()
                    .let { port.writeBytes(it, it.size.toLong()) }
                logger.log("sending")
                Thread.sleep(max(1, flags.min()!! - now - 1))
            }
        }

        launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) {
            val buffer = ByteArray(BUFFER_SIZE)
            while (true)
                port.readOrReboot(buffer, retryInterval) { }
                    .takeIf(Collection<*>::isNotEmpty)
                    ?.let(::invoke)
                    ?.let { port.writeBytes(it, it.size.toLong()) }
        }.invokeOnCompletion {
            port.closePort()
        }
    }

    private operator fun invoke(bytes: Iterable<Byte>): ByteArray? {
        val serial = mutableListOf<Byte>()
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
                            val current = pack.getShort().let(rudderEncoder::toAngular)
                            tcu.position = Stamped(now, current)
                            logger.log("rudder encoder received")
                            if (enabled) {
                                // 优化控制量
                                val (l, r, t) = optimize(current)
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
        return serial.toByteArray()
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
        val `ln-1` = ecuL.position.data.value
        val `rn-1` = ecuR.position.data.value
        ecuL.position = Stamped(t, l)
        ecuR.position = Stamped(t, r)
        val delta = structure.toDeltaOdometry(
                (l.value - `ln-1`).toRad(),
                (r.value - `rn-1`).toRad())
        odometry = Stamped(t, odometry.data plusDelta delta)
        launch { robotOnOdometry.send(odometry) }
    }

    // 生成目标控制量 -> 等轨迹限速 -> 变轨迹限速 -> 生成轮速域控制量
    private fun optimize(currentRudder: Angle): Triple<Angle, Angle, Angle?> {
        // 处理奇点
        val any = target
        val physical = when (any) {
            is Physical -> any
            is Wheels   -> any.let(structure::toPhysical)
            is Velocity -> any.let(structure::toPhysical)
        }
        if (!physical.rudder.value.isFinite())
            return Triple(0.toRad(), 0.toRad(), null)
        // 计算限速系数
        val kl: Double
        val kr: Double
        val kv: Double
        val kw: Double
        when (any) {
            is Physical -> any.let(structure::toWheels)
            is Wheels   -> any
            is Velocity -> any.let(structure::toWheels)
        }
            .let(structure::toAngular)
            .let { (l, r) ->
                kl = l.wheelSpeedLimit()
                kr = r.wheelSpeedLimit()
            }
        when (any) {
            is Physical -> any.let(structure::toVelocity)
            is Wheels   -> any.let(structure::toVelocity)
            is Velocity -> any
        }
            .limit()
            .let { (_v, _w) ->
                kv = _v
                kw = _w
            }

        // 优化实际轨迹
        return physical
            .run {
                // 不改变目标轨迹的限速
                val k0 = sequenceOf(1.0, kl, kr, kv, kw).map(::abs).min()!!
                // 因为目标轨迹无法实现产生的限速
                val k1 = 1 - abs(rudder.asRadian() - currentRudder.asRadian()) / optimizeWidthRad
                // 实际可行的控制量
                Physical(speed * k0 * max(.0, k1), currentRudder)
            }
            .let(structure::toWheels)
            .let(structure::toAngular)
            .let { (l, r) -> Triple(l, r, physical.rudder) }
    }

    // 计算轮速域限速系数
    private fun Angle.wheelSpeedLimit() =
        maxWheelSpeedRad / this.asRadian()

    // 计算速度域限速系数
    private fun Velocity.limit() =
        maxV / this.v to maxWRad / this.w.asRadian()

    private class Sender {
        private val list = mutableListOf<Byte>()

        fun append(iterable: Iterable<Byte>) {
            list.addAll(iterable)
        }

        fun build() = list.toByteArray()
    }


    private companion object {
        const val BUFFER_SIZE = 28

        val activateBytes =
            sequenceOf(CanNode.ECU().currentPositionTx,
                       CanNode.TCU(0).currentPositionTx,
                       CanNode.VCU(0).batteryPercentTx)
                .map(AutoCANPackageHead.WithoutData::pack)
                .map(ByteArray::asList)
                .flatten()
                .toList()
                .toByteArray()

        fun SerialPort.send(block: Sender.() -> Unit) {
            Sender().apply(block).build().let { writeBytes(it, it.size.toLong()) }
        }

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
