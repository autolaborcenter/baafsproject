package org.mechdancer.exceptions

/** 可恢复的异常状态 */
open class RecoverableException(message: String)
    : ApplicationException(message) {
    override fun toString() = "${this::class.java.simpleName}: $message"
}
