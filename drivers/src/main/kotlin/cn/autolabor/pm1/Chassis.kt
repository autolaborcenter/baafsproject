package cn.autolabor.pm1

import cn.autolabor.autocan.PM1Pack
import cn.autolabor.autocan.engine
import org.mechdancer.ClampMatcher
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.geometry.angle.Angle
import org.mechdancer.geometry.angle.rotate
import org.mechdancer.geometry.angle.unaryMinus
import java.io.ByteArrayInputStream
import java.io.DataInputStream

/**
 * 机器人底盘（外设无关）
 */
class Chassis(
    private val wheelsEncoder: IncrementalEncoder,
    private val rudderEncoder: IncrementalEncoder,
    private val structure: ChassisStructure,

    private val maxEncoderInterval: Long,
    private val optimizeWidth: Angle
) {
    private val engine = engine()
    private val wheelsEncoderMatcher = ClampMatcher<Stamped<Int>, Stamped<Int>>(false)

    private val ecuL = CanNode.ECU(0)
    private val ecuR = CanNode.ECU(1)
    private val tcu = CanNode.TCU(0)
    private val vcu = CanNode.VCU(0)

    private var odometry = Stamped(0L, Odometry.pose())

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
                            val (t, data) =
                                wheelsEncoderMatcher.match2()
                                    ?.takeIf(::checkInterval)
                                    ?.takeIf { (new, _, _) -> new > ecuR.position }
                                    ?.let(::interpolateMatcher)
                                ?: return@engine
                            val (r, l) = data
                            updateOdometry(t, wheelsEncoder[l], wheelsEncoder[r])
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
                            updateOdometry(t, wheelsEncoder[l], wheelsEncoder[r])
                        }
                        // 舵轮编码器
                        tcu.currentPositionRx  -> {
                            tcu.position = Stamped(now, rudderEncoder[pack.getShort().toInt()])
                        }
                    }
            }
        }
    }

    // 检查两轮数据时间差
    private fun checkInterval(
        matcher: Triple<Stamped<Int>, Stamped<Int>, Stamped<Int>>
    ): Boolean {
        val (_, before, after) = matcher
        return after.time in before.time..before.time + maxEncoderInterval
    }

    // 对编码器做插值匹配
    private fun interpolateMatcher(
        matcher: Triple<Stamped<Int>, Stamped<Int>, Stamped<Int>>
    ): Stamped<Pair<Number, Number>> {
        val (new, before, after) = matcher
        val k = (after.time - new.time).toDouble() / (after.time - before.time)
        val interpolation = before.data * k + after.data * (1 - k)
        return Stamped(new.time, new.data to interpolation)
    }

    // 更新里程计
    private fun updateOdometry(t: Long, l: Angle, r: Angle) {
        val `ln-1` = ecuL.position.data
        val `rn-1` = ecuR.position.data
        ecuL.position = Stamped(t, l)
        ecuR.position = Stamped(t, r)
        val delta = structure.toDeltaOdometry(l rotate -`ln-1`, r rotate -`rn-1`)
        odometry = Stamped(t, odometry.data plusDelta delta)
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
    }
}
