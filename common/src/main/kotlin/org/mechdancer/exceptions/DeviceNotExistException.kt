package org.mechdancer.exceptions

/** 找不到设备异常 */
open class DeviceNotExistException(what: String)
    : ApplicationException("failed to open $what until timeout")
