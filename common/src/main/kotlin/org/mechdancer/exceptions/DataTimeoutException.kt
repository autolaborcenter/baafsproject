package org.mechdancer.exceptions

/** 数据中断异常 */
open class DataTimeoutException(device: String)
    : ApplicationException("no $device data received until timeout")
