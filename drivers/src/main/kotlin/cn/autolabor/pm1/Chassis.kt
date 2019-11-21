package cn.autolabor.pm1

import cn.autolabor.autocan.PM1Pack
import cn.autolabor.autocan.engine
import org.mechdancer.ClampMatcher
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.geometry.angle.Angle
import org.mechdancer.geometry.angle.rotate
import org.mechdancer.geometry.angle.times
import org.mechdancer.geometry.angle.unaryMinus
import java.io.ByteArrayInputStream
import java.io.DataInputStream

class Chassis(
    private val maxEncoderInterval: Long,
    private val wheelsEncoder: IncrementalEncoder,
    private val rudderEncoder: IncrementalEncoder,
    private val structure: ChassisStructure
) {
    private val engine = engine()
    private val wheelsEncoderMatcher = ClampMatcher<Stamped<Int>, Stamped<Int>>(false)

    private val ecuL = CanNode.ECU(0)
    private val ecuR = CanNode.ECU(1)
    private val tcu = CanNode.TCU(0)
    private val vcu = CanNode.VCU(0)

    private var odometry = Stamped(0L, Odometry.pose())

    // 定时发送
    // 接收左右轮时更新里程计
    // 接收后轮时发送控制指令

    private fun updateEncoders(t: Long, l: Angle, r: Angle) {
        val `ln-1` = ecuL.position.data
        val `rn-1` = ecuR.position.data
        ecuL.position = Stamped(t, l)
        ecuR.position = Stamped(t, r)
        odometry = Stamped(t, odometry.data plusDelta structure.transform(l rotate -`ln-1`, r rotate -`rn-1`))
    }

    private fun checkInterval(matcher: Triple<Stamped<Int>, Stamped<Int>, Stamped<Int>>): Boolean {
        val (_, before, after) = matcher
        return after.time in before.time..before.time + maxEncoderInterval
    }

    fun parse(bytes: List<Byte>) {
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
                            val (new, before, after) =
                                wheelsEncoderMatcher.match2()
                                    ?.takeIf(::checkInterval)
                                    ?.takeIf { (new) -> new > ecuR.position }
                                ?: return@engine
                            val k = (after.time - new.time).toDouble() / (after.time - before.time)
                            updateEncoders(t = new.time,
                                           l = wheelsEncoder[before.data] * k rotate wheelsEncoder[after.data] * (1 - k),
                                           r = wheelsEncoder[new.data])
                        }
                        // 右轮编码器
                        ecuR.currentPositionRx -> {
                            wheelsEncoderMatcher.add2(Stamped(now, pack.getInt()))
                            val (new, before, after) =
                                wheelsEncoderMatcher.match1()
                                    ?.takeIf(::checkInterval)
                                    ?.takeIf { (new) -> new > ecuL.position }
                                ?: return@engine
                            val k = (after.time - new.time).toDouble() / (after.time - before.time)
                            updateEncoders(t = new.time,
                                           l = wheelsEncoder[new.data],
                                           r = wheelsEncoder[before.data] * k rotate wheelsEncoder[after.data] * (1 - k))
                        }
                        // 舵轮编码器
                        tcu.currentPositionRx  -> {
                            tcu.position = Stamped(now, rudderEncoder[pack.getShort().toInt()])
                        }
                    }
            }
        }
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
                .reversedArray()
                .let(::ByteArrayInputStream)
                .let(::DataInputStream)
                .readInt()

        fun PM1Pack.WithData.getShort() =
            data.copyOfRange(0, Short.SIZE_BYTES)
                .reversedArray()
                .let(::ByteArrayInputStream)
                .let(::DataInputStream)
                .readShort()
    }
}
