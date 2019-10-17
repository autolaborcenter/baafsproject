package com.marvelmind

import org.mechdancer.exceptions.RecoverableException

sealed class MobileBeaconException(message: String)
    : RecoverableException(message) {
    object DisconnectedException : MobileBeaconException("disconnected")
    object ParseTimeoutException : MobileBeaconException("cannot build any package for a while")
    object DataTimeoutException : MobileBeaconException("no valid data for a while")
}
