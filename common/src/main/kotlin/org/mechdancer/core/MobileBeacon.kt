package org.mechdancer.core

import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.common.Stamped

/** 移动标签接口 */
interface MobileBeacon {
    val location: Stamped<Vector2D>
}
