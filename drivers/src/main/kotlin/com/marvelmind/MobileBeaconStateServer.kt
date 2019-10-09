package com.marvelmind

import org.mechdancer.SimpleLogger
import org.mechdancer.exceptions.ExceptionMessage
import java.util.concurrent.atomic.AtomicReference

class MobileBeaconStateServer(
    private val logger: SimpleLogger
) {
    private val state = AtomicReference<MobileBeaconException?>(null)

    fun update(new: ExceptionMessage<MobileBeaconException>) {

    }
}
