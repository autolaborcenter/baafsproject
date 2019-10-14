package cn.autolabor.pathfollower

import org.mechdancer.exceptions.RecoverableException

object FollowFailedException : RecoverableException("failed to follow")
