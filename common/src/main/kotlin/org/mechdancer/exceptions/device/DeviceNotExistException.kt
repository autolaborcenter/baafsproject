package org.mechdancer.exceptions.device

import org.mechdancer.exceptions.ApplicationException

/** 找不到设备异常 */
open class DeviceNotExistException(what: String, why: String? = null)
    : ApplicationException("failed to open $what${why?.let { ":\n$it" } ?: ""}")
