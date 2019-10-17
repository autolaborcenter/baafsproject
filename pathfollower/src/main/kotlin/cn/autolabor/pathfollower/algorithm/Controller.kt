package cn.autolabor.pathfollower.algorithm

import org.mechdancer.common.filters.Filter

object UnitController : Filter<Double, Double> {
    override fun update(new: Double, time: Long?) = new
    override fun clear() = Unit
}

class Proportion(private val k: Double) : Filter<Double, Double> {
    override fun update(new: Double, time: Long?) = k * new
    override fun clear() = Unit
}
