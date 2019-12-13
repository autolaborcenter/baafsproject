package com.marvelmind.mobilebeacon

import org.mechdancer.algebra.implement.vector.Vector3D

data class MobileBeaconData(
    val coordinate: Vector3D,
    val available: Boolean,
    val quality: Byte,
    val rawDistance: Map<Byte, Double>)
