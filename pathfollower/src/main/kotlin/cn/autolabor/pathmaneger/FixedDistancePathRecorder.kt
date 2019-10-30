package cn.autolabor.pathmaneger

import org.mechdancer.algebra.function.vector.minus
import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.common.Odometry
import java.io.File

/**
 * 间隔 [interval] 记录的路径管理器
 */
class FixedDistancePathRecorder(private val interval: Double) {
    private val path = mutableListOf<Odometry>()

    /** 已记录的路径点数 */
    val size get() = path.size

    /** 获取路径列表 */
    fun get() = path.toList()

    /** 记录路径点 [new]，返回是否记录 */
    fun record(new: Odometry) =
        path.lastOrNull()
            .let { it == null || (it.p - new.p).norm() >= interval }
            .also { if (it) path += new }

    /** 记录路径点 [new] */
    operator fun plusAssign(new: Odometry) {
        record(new)
    }

    /** 清空记录的路径点 */
    fun clear() = path.clear()

    /** 保存到 [file] */
    fun saveTo(file: File) =
        file.writeText(path.joinToString("\n") { (p, d) -> "${p.x},${p.y},${d.asRadian()}" })
}
