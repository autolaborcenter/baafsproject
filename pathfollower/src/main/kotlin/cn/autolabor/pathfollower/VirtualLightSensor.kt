package cn.autolabor.pathfollower

import cn.autolabor.pathfollower.PathFollower.ControlValue
import cn.autolabor.transform.Transformation
import org.mechdancer.algebra.core.Vector
import org.mechdancer.algebra.function.vector.minus
import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.to2D

/** 位于 [fromBaseLink] 位置处具有 [lightRange] 形状的虚拟光感 */
class VirtualLightSensor(
    private val fromBaseLink: Transformation,
    private val lightRange: Shape
) {
    // DELETE ME
    val fromBaseLinkTemp get() = fromBaseLink

    // DELETE ME
    var sensorRangeTemp = lightRange.vertex

    /** 局部路径（地图坐标系） */
    var local = listOf<Vector2D>()
        private set

    /** 虚拟光值计算 */
    operator fun invoke(
        baseLinkFromMap: Transformation,
        path: Iterable<Vector2D>
    ): ControlValue {
        // 地图到传感器坐标的变换
        val sensorFromMap =
            fromBaseLink * baseLinkFromMap
        // 转化路径到传感器坐标系并约束局部路径
        val local =
            path.asSequence()
                .map(sensorFromMap::invoke)
                .map(Vector::to2D)
                .dropWhile { it !in lightRange }
                .takeWhile { it in lightRange }
                .toList()
        val sensorToMap = -sensorFromMap
        this.local = local
            .asSequence()
            .map(sensorToMap::invoke)
            .map(Vector::to2D)
            .toList()
        sensorRangeTemp =
            lightRange
                .vertex
                .asSequence()
                .map(sensorToMap::invoke)
                .map(Vector::to2D)
                .toList()
        // 处理路径丢失情况
        if (local.isEmpty())
            TODO("处理路径丢失情况")
        // 传感器栅格化
        val shape = lightRange.vertex
        // 离局部路径终点最近的点序号
        val index0 = shape
            .asSequence()
            .mapIndexed { i, p ->
                i to (p - local.last()).norm()
            }
            .minBy { (_, distance) -> distance }!!
            .first
        // 确定填色区域
        val area =
            shape
                .asSequence()
                .mapIndexed { i, p ->
                    i to (p - local.first()).norm()
                }
                .minBy { (_, distance) -> distance }!!
                .first
                .let {
                    if (it < index0)
                        it - index0 + 1 + shape.size
                    else
                        it - index0 + 1
                }
                .let {
                    local + List(it) { i -> shape[(index0 + i) % shape.size] }
                }
                .let(::Shape)
        // 计算误差
        return ControlValue(
            path.indexOfFirst { (this.local.first() - it).norm() < 0.01 },
            2 * (0.5 - area.size / lightRange.size)
        )
    }
}
