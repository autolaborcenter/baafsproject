package cn.autolabor.pathmaneger

import org.mechdancer.algebra.function.vector.minus
import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import java.io.File

/**
 * 间隔 [interval] 记录的路径管理器
 */
class PathManager(private val interval: Double) {
    private val path = mutableListOf<Vector2D>()

    /** 已记录的路径点数 */
    val size get() = path.size

    /** 获取路径列表 */
    fun get() = path.toList()

    /** 记录路径点 [p]，返回是否记录 */
    fun record(p: Vector2D) =
        path.lastOrNull()
            .let { it == null || (it - p).norm() >= interval }
            .also { if (it) path += p }

    /** 清空记录的路径点 */
    fun clear() = path.clear()

    /** 保存到文件 */
    fun saveTo(file: File) =
        file.writeText(path.joinToString("\n") { "${it.x}, ${it.y}" })

    /** 从文件加载 */
    fun loadFrom(file: File) =
        file.readLines()
            .map {
                val comma = it.indexOf(',')
                vector2DOf(it.substring(0 until comma).toDouble(),
                           it.substring(comma + 1).toDouble())
            }
            .let {
                path.clear()
                path.addAll(it)
                Unit
            }
}
