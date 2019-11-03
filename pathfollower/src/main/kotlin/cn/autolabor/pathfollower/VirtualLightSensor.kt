package cn.autolabor.pathfollower

import org.mechdancer.DebugTemporary
import org.mechdancer.DebugTemporary.Operation.DELETE
import org.mechdancer.algebra.function.vector.dot
import org.mechdancer.algebra.function.vector.minus
import org.mechdancer.algebra.function.vector.normalize
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.common.Odometry
import org.mechdancer.common.invoke
import org.mechdancer.common.toTransformation
import org.mechdancer.geometry.angle.toVector
import org.mechdancer.simulation.map.shape.AnalyticalShape
import org.mechdancer.simulation.map.shape.Polygon
import org.mechdancer.simulation.map.shape.Shape

/**
 * 虚拟光感
 *
 * @param onRobot 位姿
 * @param lightRange 光斑形状
 */
class VirtualLightSensor(
    onRobot: Odometry,
    lightRange: Shape
) {
    private val lightRange = when (lightRange) {
        is Polygon         -> lightRange
        is AnalyticalShape -> lightRange.sample().toList().let(::Polygon)
        else               -> throw IllegalArgumentException()
    }
    private val robotToSensor = onRobot.toTransformation().inverse()

    @DebugTemporary(DELETE)
    val sensorToRobot = onRobot.toTransformation()
    @DebugTemporary(DELETE)
    var area = listOf<Vector2D>()
        private set

    /**
     * 照亮
     *
     * 从目标路径获取兴趣区段
     * 获取到的列表中点位于传感器坐标系
     */
    fun shine(path: Sequence<Odometry>) =
        path.map { pose -> pose to robotToSensor(pose.p).to2D() }
            .dropWhile { (_, p) -> p !in lightRange }
            .takeWhile { (_, p) -> p in lightRange }
            .map { (pose, _) -> pose }
            .toList()

    /** 虚拟光值计算 */
    operator fun invoke(path: List<Odometry>): Double {
        // 转化路径到传感器坐标系并约束局部路径
        val local = path.map { robotToSensor(it) }
        // 处理路径丢失情况
        if (local.isEmpty()) return .0
        // 传感器栅格化
        val shape = lightRange.vertex
        // 离局部路径终点最近的点序号
        val index0 = shape.indexNear(local.last(), false)
        val index1 = shape.indexNear(local.first(), true)
            .let { if (it < index0) it + shape.size else it }
        // 确定填色区域
        val area = Polygon(local.map { it.p } + List(index1 - index0 + 1) { i -> shape[(index0 + i) % shape.size] })
        this.area = area.vertex.map { sensorToRobot(it).to2D() }
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
