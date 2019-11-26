package cn.autolabor.pm1.model

import org.mechdancer.geometry.angle.Angle
import org.mechdancer.geometry.angle.toRad
import kotlin.math.abs
import kotlin.math.max

/** 优化器 */
internal class Optimizer(
    maxWheelSpeed: Angle,     // 最大轮速
    private val maxV: Double, // 最大线速度
    maxW: Angle,              // 最大角速度
    optimizeWidth: Angle,     // 后轮优化宽度
    maxAccelerate: Double,    // 最大线加速度
    controlPeriod: Long       // 控制周期
) {
    private val maxWheelSpeedRad = maxWheelSpeed.asRadian()
    private val maxWRad = maxW.asRadian()
    private val optimizeWidthRad = optimizeWidth.asRadian()
    private val maxSpeedIncremental = maxAccelerate * 2 * controlPeriod / 1000

    data class Optimized(
        val speed: Double,
        val left: Angle,
        val right: Angle,
        val rudder: Angle?)

    // 生成目标控制量 -> 等轨迹限速 -> 变轨迹限速 -> 生成轮速域控制量
    operator fun invoke(target: ControlVariable,
                        current: ControlVariable.Physical,
                        structure: ChassisStructure
    ): Optimized {
        // 处理奇点
        val physical = when (target) {
            is ControlVariable.Physical -> target
            is ControlVariable.Wheels   -> target.let(structure::toPhysical)
            is ControlVariable.Velocity -> target.let(structure::toPhysical)
        }
        if (!physical.rudder.value.isFinite()) return Optimized(.0, zeroAngle, zeroAngle, null)
        // 计算限速系数
        val kl: Double
        val kr: Double
        val kv: Double
        val kw: Double
        when (target) {
            is ControlVariable.Physical -> target.let(structure::toWheels)
            is ControlVariable.Wheels   -> target
            is ControlVariable.Velocity -> target.let(structure::toWheels)
        }
            .let(structure::toAngular)
            .let { (l, r) ->
                kl = l.wheelSpeedLimit()
                kr = r.wheelSpeedLimit()
            }
        when (target) {
            is ControlVariable.Physical -> target.let(structure::toVelocity)
            is ControlVariable.Wheels   -> target.let(structure::toVelocity)
            is ControlVariable.Velocity -> target
        }
            .limit()
            .let { (_v, _w) ->
                kv = _v
                kw = _w
            }
        // 优化实际轨迹
        val optimized = physical
            .run {
                // 不改变目标轨迹的限速
                val k0 = sequenceOf(1.0, kl, kr, kv, kw).map(::abs).min()!!
                // 因为目标轨迹无法实现产生的限速
                val k1 = 1 - abs(rudder.asRadian() - current.rudder.asRadian()) / optimizeWidthRad
                // 实际可行的目标轮速
                val actual = (speed * k0 * max(.0, k1)).clamp(current.speed)
                // 实际可行的控制量
                ControlVariable.Physical(actual, current.rudder)
            }
        return optimized
            .let(structure::toWheels)
            .let(structure::toAngular)
            .let { (l, r) -> Optimized(optimized.speed, l, r, physical.rudder) }
    }

    // 计算轮速域限速系数
    private fun Angle.wheelSpeedLimit() =
        maxWheelSpeedRad / this.asRadian()

    // 计算速度域限速系数
    private fun ControlVariable.Velocity.limit() =
        maxV / this.v to maxWRad / this.w.asRadian()

    // 计算缓起缓停
    private fun Double.clamp(current: Double): Double {
        val min = current - maxSpeedIncremental
        val max = current + maxSpeedIncremental
        return when {
            this < min -> min
            this < max -> this
            else       -> max
        }
    }

    private companion object {
        val zeroAngle = 0.toRad()
    }
}
