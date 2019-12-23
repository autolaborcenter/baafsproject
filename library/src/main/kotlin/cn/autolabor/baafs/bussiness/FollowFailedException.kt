package cn.autolabor.baafs.bussiness

import org.mechdancer.exceptions.RecoverableException

/** 循径失败异常 */
object FollowFailedException : RecoverableException("failed to follow")
