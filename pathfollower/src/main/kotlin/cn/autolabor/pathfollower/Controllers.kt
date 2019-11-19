package cn.autolabor.pathfollower

import org.mechdancer.common.filters.Filter

/** 单位控制器 */
object UnitController : Filter<Double, Double> {
    override fun update(new: Double, time: Long?) = new
    override fun clear() = Unit
}

/** 比例环节 */
class Proportion(private val k: Double) : Filter<Double, Double> {
    override fun update(new: Double, time: Long?) = k * new
    override fun clear() = Unit
}

/** 比例积分环节 */
class PIController(
    private val k: Double,
    private val ki: Double,
    private val kMemory: Double
) : Filter<Double, Double> {
    private var i = .0

    override fun update(new: Double, time: Long?): Double {
        i = kMemory * i + (1 - kMemory) * new
        return k * (new + ki * i)
    }

    override fun clear() {
        i = .0
    }
}
