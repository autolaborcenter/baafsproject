package cn.autolabor.baafs.collisionpredictor

import org.mechdancer.exceptions.RecoverableException

/** 碰撞预警 */
object CollisionDetectedException
    : RecoverableException("collision detected")
