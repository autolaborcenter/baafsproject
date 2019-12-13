package com.marvelmind.mobilebeacon

import com.marvelmind.mobilebeacon.BeaconPackage.*
import com.marvelmind.mobilebeacon.BeaconPackage.RawDistance.Distance
import org.mechdancer.algebra.implement.vector.vector3DOf
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
                val a = last.data
                val b = rawDistance?.takeIf { it > last }?.data
                val c = quality?.takeIf { it > last }?.data
                this.coordinate = coordinate
                rawDistance = null
                quality = null
                MobileBeaconData(
                    address = a.address,
                    coordinate = vector3DOf(a.x / 1000.0, a.y / 1000.0, a.x / 1000.0),
                    available = a.available,
                    quality = c?.qualityPercent,
                    rawDistance = b?.let {
                        mapOf(it.d0.toPair(),
                              it.d1.toPair(),
                              it.d2.toPair(),
                              it.d3.toPair())
                    })
            }
        }

    @Synchronized
    fun updateRawDistance(rawDistance: Stamped<RawDistance>): MobileBeaconData? {
        this.rawDistance = rawDistance
        return collect()
    }

    @Synchronized
    fun updateQuality(quality: Stamped<Quality>): MobileBeaconData? {
        this.quality = quality
        return collect()
    }

    private fun collect(): MobileBeaconData? {
        val (ta, a) = coordinate ?: return null
        val (tb, b) = rawDistance ?: return null
        val (tc, c) = quality ?: return null
        if (tb in ta..tc && tc - ta < 20) {
            coordinate = null
            rawDistance = null
            quality = null
            return MobileBeaconData(
                address = a.address,
                coordinate = vector3DOf(a.x / 1000.0, a.y / 1000.0, a.x / 1000.0),
                available = a.available,
                quality = c.qualityPercent,
                rawDistance = mapOf(b.d0.toPair(),
                                    b.d1.toPair(),
                                    b.d2.toPair(),
                                    b.d3.toPair()))
        }
        return null
    }

    private companion object {
        fun Distance.toPair() = let { (address, value) -> address to value / 1000.0 }
    }
}
