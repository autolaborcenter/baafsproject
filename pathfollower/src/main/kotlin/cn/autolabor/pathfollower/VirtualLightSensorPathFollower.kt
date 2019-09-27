package cn.autolabor.pathfollower

import cn.autolabor.pathfollower.VirtualLightSensorPathFollower.FollowCommand.Follow
import cn.autolabor.pathfollower.VirtualLightSensorPathFollower.FollowCommand.Turn
import org.mechdancer.algebra.function.vector.dot
import org.mechdancer.algebra.function.vector.norm
import org.mechdancer.common.Odometry
import org.mechdancer.common.toPose
import org.mechdancer.geometry.angle.adjust
import org.mechdancer.geometry.angle.toRad
import org.mechdancer.geometry.angle.toVector
import org.mechdancer.geometry.transformation.Transformation
import kotlin.math.*

/**
 * 使用 [sensor] 从路径生成误差信号的循径控制器
 */
class VirtualLightSensorPathFollower(
    val sensor: VirtualLightSensor,
    private val controller: Controller = Controller.unit,
    private val tipOrderRange: IntRange = 3..3,
    private val tipJudge: Double = PI / 3,
    private val destinationJudge: Double = 0.1
) {
    private var pass = 0
    private val pathMarked = mutableListOf<Pair<Odometry, Double>>()
    private var pre = .0

    /** 读写工作路径 */
    var path = listOf<Odometry>()
        set(value) {
            // 存储
            field = value
            pathMarked.clear()
            for (i in 1 until value.size) {
                val v0 = value[i - 1].d.toVector()
                val v1 = value[i].d.toVector()
                pathMarked.add(value[i] to (v0 dot v1))
            }
            pathMarked.add(value.last() to 2.0)
            // 重置状态
            pass = 0
            controller.clear()
        }

    sealed class FollowCommand {
        data class Follow(val v: Double, val w: Double) : FollowCommand()
        data class Turn(val angle: Double) : FollowCommand()
        object Error : FollowCommand() {
            override fun toString() = "Error"
        }

        object Finish : FollowCommand() {
            override fun toString() = "Finish"
        }
    }

    operator fun invoke(fromMap: Transformation): FollowCommand {
        val limit = if (pass == 0) path.size else min(pass + 40, path.size)
        // 第一次调用传感器
        val (passCount, value) =
            sensor(fromMap, pathMarked.subList(pass, limit).map(Pair<Odometry, *>::first))
                .takeUnless { (passCount, _) -> passCount < 0 }
            ?: return if (abs(pre) > tipJudge / 2) Turn(pre) else FollowCommand.Error
        // 判断路径终点
        listOf(sensor.local.last(), pathMarked.last().first)
            .map { fromMap(it.p).norm() }
            .all { it < destinationJudge }
            .let { if (it) return FollowCommand.Finish }
        // 丢弃通过的路径
        pass += passCount
        val next = pathMarked.subList(pass, min(pass + max(4, sensor.local.size), pathMarked.size))
        // 利用缓存识别尖点
        return next.asSequence()
            .mapIndexed { i, item -> item to i }
            .firstOrNull { (it, _) -> it.second < cos(tipJudge) }
            // 处理尖点
            ?.let { (item, i) ->
                println(i)
                when {
                    i in 1..4 -> {
                        val target = item.first.d.asRadian()
                        val current = (-fromMap).toPose().d.asRadian()
                        pre = (target - current).toRad().adjust().asRadian()
                        i
                    }
                    i > 4     -> {
                        pre = .0
                        i
                    }
                    else      -> {
                        val target = item.first.d.asRadian()
                        val current = (-fromMap).toPose().d.asRadian()
                        val delta = (target - current).toRad().adjust().asRadian()
                        if (abs(delta) > tipJudge / 2) {
                            pass += i
                            return Turn(delta)
                        }
                        null
                    }
                }
            }
            ?.let { sensor(fromMap, sensor.local.subList(0, it)) }
            ?.second
            .let { Follow(0.1, controller(input = it ?: value)) }
    }
}
