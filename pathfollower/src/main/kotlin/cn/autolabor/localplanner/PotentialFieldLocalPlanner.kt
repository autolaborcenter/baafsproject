package cn.autolabor.localplanner

import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.common.Odometry

class PotentialFieldLocalPlanner {
    /**
     * 修饰函数
     *
     * @param global 机器人坐标系上的全局路径
     * @param repulsionPoints 斥力点
     */
    fun modify(
        global: Sequence<Odometry>,
        repulsionPoints: Collection<Vector2D>
    ): Sequence<Odometry> {
        val globalIter = global.iterator()
        var lastFromGlobal = globalIter.consume() ?: return emptySequence()

        return sequence {
            var pose = Odometry.pose()
            while (true) {

            }
        }
    }

    private companion object {
        fun <T> Iterator<T>.consume() =
            takeIf(Iterator<*>::hasNext)?.next()
    }
}
