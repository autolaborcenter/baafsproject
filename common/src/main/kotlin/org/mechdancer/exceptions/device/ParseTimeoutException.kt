package org.mechdancer.exceptions.device

import org.mechdancer.exceptions.RecoverableException

/** 协议解析超时异常 */
class ParseTimeoutException(val what: String, timeout: Long)
    : RecoverableException("cannot build any package from stream of $what in $timeout ms") {
    override fun equals(other: Any?) =
        this === other || other is ParseTimeoutException && what == other.what

    override fun hashCode() =
        what.hashCode()
}
