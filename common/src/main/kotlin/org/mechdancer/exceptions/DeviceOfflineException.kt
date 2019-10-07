package org.mechdancer.exceptions

/** 设备掉线异常 */
class DeviceOfflineException(what: String)
    : ApplicationException("disconnected to $what")
