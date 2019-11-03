package org.mechdancer.device

import org.mechdancer.algebra.implement.vector.Vector2D

/**
 * 雷达系
 *
 * 支持获取最新的一帧
 */
interface LidarSet {
    val frame: List<Vector2D>
}
