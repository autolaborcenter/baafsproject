package cn.autolabor.pathfollower

import org.mechdancer.Temporary
import org.mechdancer.Temporary.Operation.DELETE
import org.mechdancer.Temporary.Operation.INLINE
import org.mechdancer.algebra.core.Vector
import org.mechdancer.algebra.function.vector.dot
import org.mechdancer.algebra.function.vector.minus
import org.mechdancer.algebra.function.vector.normalize
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.common.Odometry
import org.mechdancer.common.invoke
import org.mechdancer.common.toTransformation
import org.mechdancer.geometry.angle.toVector
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
    var local = listOf<Odometry>()
        private set

    fun findLocal(pose: Odometry,
                  path: Iterable<Odometry>
    ): IntRange {
        // 地图到传感器坐标的变换
        val mapToSensor = robotToSensor * (-pose.toTransformation())
        var pass = 0
        var take = -1
        // 转化路径到传感器坐标系并约束局部路径
        loop@ for (item in path)
            when {
                mapToSensor(item).p in lightRange -> take = if (take < 0) pass else take + 1
                take < 0                          -> ++pass
                else                              -> break@loop
            }
        return pass..take
    }

    /** 虚拟光值计算 */
    operator fun invoke(
        pose: Odometry,
        path: Iterable<Odometry>
    ): Double {
        local = path.toList()
        // 地图到传感器坐标的变换
        val sensorFromMap = robotToSensor * (-pose.toTransformation())
        // 转化路径到传感器坐标系并约束局部路径
        val local = path.map { sensorFromMap.invoke(it) }
        // 处理路径丢失情况
        if (local.isEmpty()) return .0
        // 传感器栅格化
        val shape = lightRange.vertex
        // 离局部路径终点最近的点序号
        val index0 = shape.indexNear(local.last(), false)
        val index1 = shape.indexNear(local.first(), true)
            .let { if (it < index0) it + shape.size else it }
        // 确定填色区域
        val area = Shape(local.map { it.p } + List(index1 - index0 + 1) { i -> shape[(index0 + i) % shape.size] })
        @Temporary(DELETE)
        areaShape = area.vertex.map((-sensorFromMap)::invoke).map(Vector::to2D)
        // 计算误差
        return 2 * (0.5 - area.size / lightRange.size)
    }

    private companion object {
        // 查找与边缘交点
        fun List<Vector2D>.indexNear(pose: Odometry, reverse: Boolean): Int {
            val (p, d) = pose
            val references = mapIndexed { i, item -> i to item - p }
            return if (reverse) references.minBy { (_, v) -> v.normalize() dot d.toVector() }!!.first
            else references.maxBy { (_, v) -> v.normalize() dot d.toVector() }!!.first
        }
    }
}
