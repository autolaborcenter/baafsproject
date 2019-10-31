package cn.autolabor.business

import org.mechdancer.SimpleLogger
import org.mechdancer.common.Odometry
import org.mechdancer.paintPoses
import org.mechdancer.remote.presets.RemoteHub
import java.io.File
import kotlin.math.roundToInt

/**
 * 全局路径管理
 */
class PathManager(
    private val localRadius: Double,
    pathInterval: Double,
    private val logger: SimpleLogger?,
    private val painter: RemoteHub?
) {
    private val searchCount = (localRadius / pathInterval).roundToInt()
    private val globals = mutableMapOf<String, GlobalPath>()

    /** 强制从文件中读取路径，并设置进度 */
    fun refresh(pathName: String, progress: Double = .0) =
        File(pathName)
            .takeIf(File::exists)
            ?.readLines()
            ?.map {
                val numbers = it.split(',').map(String::toDouble)
                Odometry.odometry(numbers[0], numbers[1], numbers[2])
            }
            ?.toList()
            ?.let { GlobalPath(it, localRadius, searchCount) }
            ?.also { global ->
                global.progress = progress
                globals[pathName] = global
                logger?.log("path \"$pathName\" is loaded")
                painter?.paintPoses("路径", global)
            }

    /** 本地或文件中读取路径，并设置进度 */
    fun load(pathName: String, progress: Double = .0) =
        globals[pathName]?.also { it.progress = progress } ?: refresh(pathName, progress)

    /** 从本地或文件中读取路径 */
    fun resume(pathName: String) =
        globals[pathName] ?: refresh(pathName)

    /** 将路径存储到文件 */
    fun save(fileName: String, path: List<Odometry>) =
        path.joinToString("\n") { (p, d) -> "${p.x},${p.y},${d.asRadian()}" }
            .let { File(fileName).writeText(it) }

    override fun toString() =
        buildString {
            for ((name, path) in globals)
                appendln("$name:\t${path.progress * 100}%/${path.size}")
        }
}
