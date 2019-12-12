package org.mechdancer.exceptions

/** 数据超时异常 */
class DataTimeoutException(val what: String, timeout: Long)
    : RecoverableException("cannot build any valid data from stream of $what in $timeout ms") {
    override fun equals(other: Any?) =
        this === other || other is DataTimeoutException && what == other.what

    override fun hashCode() =
        what.hashCode()
}
