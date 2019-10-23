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
