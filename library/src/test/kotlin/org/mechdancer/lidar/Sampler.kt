package org.mechdancer.lidar

/** 采样器 */
internal class Sampler(frequency: Double) {
    private val period = 1000 / frequency
    private var count = 0
    fun trySample(t: Long) =
        (t > period * count).also { if (it) ++count }
}
