package cn.autolabor.utilities.time

/**
 * 计时数据
 */
data class Stamped<T>(
    val time: Long,
    val data: T
) : Comparable<Stamped<*>> {
    override fun compareTo(other: Stamped<*>) = time.compareTo(other.time)
}
