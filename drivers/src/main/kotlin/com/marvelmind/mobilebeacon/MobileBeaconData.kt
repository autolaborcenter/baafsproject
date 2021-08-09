package com.marvelmind.mobilebeacon

data class MobileBeaconData(
    val address: Byte,
    val x: Int,
    val y: Int,
    val z: Int,
    val available: Boolean,
    val quality: Byte?,
    val rawDistance: List<Pair<Byte, Int>>?
)
