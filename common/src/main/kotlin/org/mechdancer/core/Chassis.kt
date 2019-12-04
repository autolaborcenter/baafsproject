package org.mechdancer.core

import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped

/** 底盘接口 */
interface Chassis<T> {
    /** 读写目标状态 */
    var target: T

    /** 里程计 */
    val odometry: Stamped<Odometry>

    /** 预测 */
    fun predict(target: T): (Long) -> Odometry
}
