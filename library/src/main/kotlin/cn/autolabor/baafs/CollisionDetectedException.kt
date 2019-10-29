package cn.autolabor.baafs

import org.mechdancer.exceptions.RecoverableException

/** 碰撞预警 */
object CollisionDetectedException
    : RecoverableException("collision detected")
