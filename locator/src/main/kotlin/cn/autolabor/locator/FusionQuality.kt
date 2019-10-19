package cn.autolabor.locator

data class FusionQuality(
    val age: Double,
    val location: Double,
    val direction: Double
) {
    companion object {
        val zero = FusionQuality(.0, .0, .0)
    }
}
