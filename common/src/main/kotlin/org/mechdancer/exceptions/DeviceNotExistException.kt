package org.mechdancer.exceptions

/** 找不到设备异常 */
open class DeviceNotExistException(what: String, why: String? = null)
    : ApplicationException("failed to open $what until timeout${why?.let { ":\n$it" } ?: ""}")
