package cn.autolabor

import org.mechdancer.algebra.implement.vector.Vector2D

/**
 * 位于 [p] 的路径点 [tipOrder] 阶路径尖点
 */
data class PathNode(val p: Vector2D, val tipOrder: Int = Int.MAX_VALUE)
