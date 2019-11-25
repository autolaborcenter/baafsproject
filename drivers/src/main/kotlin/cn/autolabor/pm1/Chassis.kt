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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mechdancer.ClampMatcher
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
import kotlin.math.abs
import kotlin.math.max

/**
 * 机器人底盘
 */
class Chassis(
    scope: CoroutineScope,

    private val wheelsEncoder: IncrementalEncoder,
    private val rudderEncoder: IncrementalEncoder,
    private val structure: ChassisStructure,

    private val odometryInterval: Long,
    maxWheelSpeed: Angle,
    maxVelocity: Velocity,
    optimizeWidth: Angle,

    private val retryInterval: Long
) : CoroutineScope by scope {
    private val maxWheelSpeedRad = maxWheelSpeed.asRadian()
    private val maxV = maxVelocity.v
    private val maxW = maxVelocity.w.asRadian()
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

    private val port =
        findSerialPort(
                candidates =
                SerialPort
                    .getCommPorts()
                    .filter {
                        val name = it.systemPortName.toLowerCase()
                        "com" in name || "usb" in name || "acm" in name
                    },
                engine = engine
        ) {
            bufferSize = BUFFER_SIZE
            baudRate = 115200
            timeoutMs = 500L
            activate =
                sequenceOf(CanNode.ECU().currentPositionTx,
                           tcu.currentPositionTx)
                    .map(AutoCANPackageHead.WithoutData::pack)
                    .map(ByteArray::asList)
                    .flatten()
                    .toList()
                    .toByteArray()
            var lDone = false
            var rDone = false
            var rudderDone = false
            condition { pack ->
                if (pack !is PM1Pack.WithData) return@condition false
                val now = System.currentTimeMillis()
                when (pack.head) {
                    ecuL.currentPositionRx -> {
                        val pulse = pack.getInt()
                        ecuL.position = Stamped(now, pulse.let(wheelsEncoder::toAngular))
                        wheelsEncoderMatcher.add1(Stamped(now, pulse))
                        lDone = true
                    }
                    ecuR.currentPositionRx -> {
                        val pulse = pack.getInt()
                        ecuR.position = Stamped(now, pulse.let(wheelsEncoder::toAngular))
                        wheelsEncoderMatcher.add2(Stamped(now, pulse))
                        rDone = true
                    }
                    tcu.currentPositionRx  -> {
                        val pulse = pack.getShort()
                        tcu.position = Stamped(now, pulse.let(rudderEncoder::toAngular))
                        rudderDone = true
                    }
                }
                lDone && rDone && rudderDone
            }
        }

    init {
        launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) {
            launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) {
                launchAsk(1000L, CanNode.EveryNode.stateTx)
                launchAsk(odometryInterval, CanNode.ECU().currentPositionTx)
                launchAsk(odometryInterval / 2, tcu.currentPositionTx)
            }

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
                            updateOdometry(t = t,
                                           l = l.let(wheelsEncoder::toAngular),
                                           r = r.let(wheelsEncoder::toAngular))
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
                        }
                        // 舵轮编码器
                        tcu.currentPositionRx  -> {
                            val current = pack.getShort().let(rudderEncoder::toAngular)
                            tcu.position = Stamped(now, current)
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

    private fun launchAsk(period: Long, head: AutoCANPackageHead.WithoutData) {
        launch {
            val msg = head.pack()
            while (true) {
                port.writeBytes(msg, msg.size.toLong())
                delay(period)
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
        val `ln-1` = ecuL.position.data.value
        val `rn-1` = ecuR.position.data.value
        ecuL.position = Stamped(t, l)
        ecuR.position = Stamped(t, r)
        val delta = structure.toDeltaOdometry(
                (l.value - `ln-1`).toRad(),
                (r.value - `rn-1`).toRad())
        odometry = Stamped(t, odometry.data plusDelta delta)
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
        maxV / this.v to maxW / this.w.asRadian()

    private companion object {
        const val BUFFER_SIZE = 28

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
