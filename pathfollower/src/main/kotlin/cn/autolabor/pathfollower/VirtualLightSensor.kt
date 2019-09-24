package cn.autolabor.pathfollower

import org.mechdancer.Temporary
import org.mechdancer.Temporary.Operation.DELETE
import org.mechdancer.Temporary.Operation.INLINE
import org.mechdancer.algebra.core.Vector
import org.mechdancer.algebra.function.vector.dot
import org.mechdancer.algebra.function.vector.minus
import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.algebra.function.vector.normalize
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.geometry.transformation.Transformation

/** 位于 [robotToSensor] 位置处具有 [lightRange] 形状的虚拟光感 */
class VirtualLightSensor(
    private val robotToSensor: Transformation,
    private val lightRange: Shape
) {
    @Temporary(DELETE)
    var areaShape = listOf<Vector2D>()

    /** 局部路径（地图坐标系） */
    @Temporary(INLINE)
    var local = listOf<Vector2D>()
        private set

    /** 虚拟光值计算 */
    operator fun invoke(
        mapToRobot: Transformation,
        path: Iterable<Vector2D>
    ): Pair<Int, Double> {
        // 地图到传感器坐标的变换
        val sensorFromMap =
            robotToSensor * mapToRobot
        // 转化路径到传感器坐标系并约束局部路径
        val local =
            path.asSequence()
                .map(sensorFromMap::invoke)
                .map(Vector::to2D)
                .dropWhile { it !in lightRange }
                .toList()
                .let { mapped ->
                    val end = (0 until mapped.size - 3)
                                  .firstOrNull { i ->
                                      mapped
                                          .subList(i, i + 3)
                                          .all { it !in lightRange }
                                  }
                              ?: mapped.size
                    mapped.take(end)
                }

        val sensorToMap = -sensorFromMap
        this.local = local
            .asSequence()
            .map(sensorToMap::invoke)
            .map(Vector::to2D)
            .toList()
        // 处理路径丢失情况
        if (local.size < 2) return -1 to .0
        // 传感器栅格化
        val shape = lightRange.vertex
        // 起点终点方向
        val vb = local[1] - local[0]
        val ve = local.asReversed().let { it[1] - it[0] }
        // 离局部路径终点最近的点序号
        val index0 = shape.indexNear(local.last(), ve)
        val index1 = shape.indexNear(local.first(), vb)
            .let { if (it < index0) it + shape.size else it }
        // 确定填色区域
        val area = Shape(local + List(index1 - index0) { i -> shape[(index0 + i) % shape.size] })
        @Temporary(DELETE)
        areaShape = area.vertex.map(sensorToMap::invoke).map(Vector::to2D)
        // 计算误差
        return path.indexOfFirst { (this.local.first() - it).norm() < 0.01 } to 2 * (0.5 - area.size / lightRange.size)
    }

    private companion object {
        // 查找与边缘交点
        fun List<Vector2D>.indexNear(p: Vector, d: Vector2D): Int {
            val references = mapIndexed { i, item -> i to p - item }
            return references
                       .asSequence()
                       .mapNotNull { (i, v) ->
                           v.norm()
                               .takeIf { it < 0.1 }
                               ?.let { i to it }
                       }
                       .minBy { (_, distance) -> distance }
                       ?.first
                   ?: references
                       .maxBy { (_, v) -> (v.normalize() dot d) }!!
                       .first
        }
    }
}
