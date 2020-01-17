package cn.autolabor.pm1.model

import org.mechdancer.average
import org.mechdancer.common.Stamped
import org.mechdancer.geometry.angle.Angle
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.geometry.transformation.Pose2D
import org.mechdancer.geometry.transformation.pose2D

internal class Predictor(
    private val structure: ChassisStructure,
    private val optimizer: Optimizer,
    private val controlPeriod: Long,
    rudderSpeed: Angle
) {
    private val rudderDeltaRad = rudderSpeed.asRadian() * controlPeriod / 1000

    private infix fun Double.clamp(current: Double): Double {
        val min = current - rudderDeltaRad
        val max = current + rudderDeltaRad
        return when {
            this < min -> min
            this < max -> this
            else       -> max
        }
    }

    fun predict(target: ControlVariable,
                current: ControlVariable.Physical
    ): (Long) -> Pose2D {
        val physical = when (target) {
            is ControlVariable.Physical -> target
            is ControlVariable.Wheels   -> target.let(structure::toPhysical)
            is ControlVariable.Velocity -> target.let(structure::toPhysical)
        }
        var sn = current
        val cache = mutableListOf(Stamped(0L, pose2D()))
        return { tt: Long ->
            when {
                tt <= 0                 ->
                    cache[0].data
                tt <= cache.last().time -> {
                    val i = cache.binarySearch { (t, _) -> (t - tt).toInt() }
                    if (i > 0) cache[i].data
                    else {
                        val (t0, p0) = cache[-i - 1]
                        val (t1, p1) = cache[-i]
                        val k = (t1 - tt).toDouble() / (t1 - t0)
                        average(p0 to k, p1 to 1 - k)
                    }
                }
                else                    -> {
                    var result: Pose2D? = null
                    while (result == null) {
                        // 保存上一时刻状态
                        val (`tn-1`, `pn-1`) = cache.last()
                        // 更新后轮转角
                        val rudder =
                            if (!physical.rudder.value.isFinite())
                                sn.rudder
                            else
                                (physical.rudder.asRadian() clamp sn.rudder.asRadian()).toRad()
                        // 如常执行优化
                        val (speed, _, _, _) = optimizer(target, sn.copy(rudder = rudder))
                        // 保存状态
                        sn = ControlVariable.Physical(speed, rudder)
                        // 计算里程增量
                        val (v, w) = sn.let(structure::toVelocity)
                        val delta =
                            org.mechdancer.common.Velocity
                                .NonOmnidirectional(v, w.asRadian())
                                .toDeltaOdometry(controlPeriod / 1000.0)
                        // 更新缓存
                        val tn = `tn-1` + controlPeriod
                        val pn = `pn-1` plusDelta delta
                        cache += Stamped(tn, pn)
                        // 计算返回条件
                        when {
                            tn == tt ->
                                result = pn
                            tn > tt  -> {
                                val k = (tn - tt).toDouble() / (tn - `tn-1`)
                                result = average(`pn-1` to k, pn to 1 - k)
                            }
                        }
                    }
                    result
                }
            }
        }
    }
}
