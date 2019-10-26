package org.mechdancer.exceptions.device

import org.mechdancer.exceptions.RecoverableException

/** 设备掉线异常 */
class DeviceOfflineException(val what: String)
    : RecoverableException("disconnected to $what") {
    override fun equals(other: Any?) =
        this === other ||  other is DeviceOfflineException && what == other.what

    override fun hashCode() =
        what.hashCode()
}
