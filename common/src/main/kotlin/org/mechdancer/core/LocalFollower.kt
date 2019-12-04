package org.mechdancer.core

import org.mechdancer.common.Odometry

/** 循径控制器接口 */
interface LocalFollower<T : Any> {
    /** 计算控制量 */
    operator fun invoke(local: Sequence<Odometry>): T?
}
