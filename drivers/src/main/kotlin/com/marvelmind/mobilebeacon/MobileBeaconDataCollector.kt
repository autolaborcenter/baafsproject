package com.marvelmind.mobilebeacon

import com.marvelmind.mobilebeacon.BeaconPackage.*
import com.marvelmind.mobilebeacon.BeaconPackage.RawDistance.Distance
import org.mechdancer.common.Stamped

internal class MobileBeaconDataCollector {
    private var coordinate: Stamped<Coordinate>? = null
    private var rawDistance: Stamped<RawDistance>? = null
    private var quality: Stamped<Quality>? = null

    @Synchronized
    fun updateCoordinate(coordinate: Stamped<Coordinate>) =
        when (val last = this.coordinate) {
            null -> {
                this.coordinate = coordinate
                collect()
            }
            else -> {
                val (t, a) = last
                val b = rawDistance?.takeIf { it > last }?.data
                val c = quality?.takeIf { it > last }?.data
                this.coordinate = coordinate
                rawDistance = null
                quality = null
                Stamped(t, MobileBeaconData(
                        address = a.address,
                        x = a.x,
                        y = a.y,
                        z = a.z,
                        available = a.available,
                        quality = c?.qualityPercent,
                        rawDistance = b?.toMap())
                )
            }
        }

    @Synchronized
    fun updateRawDistance(rawDistance: Stamped<RawDistance>)
            : Stamped<MobileBeaconData>? {
        this.rawDistance = rawDistance
        return collect()
    }

    @Synchronized
    fun updateQuality(quality: Stamped<Quality>)
            : Stamped<MobileBeaconData>? {
        this.quality = quality
        return collect()
    }

    private fun collect(): Stamped<MobileBeaconData>? {
        val (ta, a) = coordinate ?: return null
        val (tb, b) = rawDistance ?: return null
        val (tc, c) = quality ?: return null
        if (tb in ta..tc && tc - ta < 20) {
            coordinate = null
            rawDistance = null
            quality = null
            return Stamped(ta, MobileBeaconData(
                    address = a.address,
                    x = a.x,
                    y = a.y,
                    z = a.z,
                    available = a.available,
                    quality = c.qualityPercent,
                    rawDistance = b.toMap()))
        }
        return null
    }

    private companion object {
        fun Distance.toPair() =
            let { (address, value) -> address to value }

        fun RawDistance.toMap() =
            mapOf(d0.toPair(), d1.toPair(), d2.toPair(), d3.toPair())
    }
}
