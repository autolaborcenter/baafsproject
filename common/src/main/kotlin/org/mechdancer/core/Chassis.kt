package org.mechdancer.core

import org.mechdancer.common.Stamped
import org.mechdancer.geometry.transformation.Pose2D

/** 底盘接口 */
interface Chassis<T> {
    /** 读写目标状态 */
    var target: T

    /** 里程计 */
    val odometry: Stamped<Pose2D>

    /** 预测 */
    fun predict(target: T): (Long) -> Pose2D
}
