package cn.autolabor.autocan

private fun ByteArray.toHexString() =
    joinToString(" ") { byte ->
        Integer
            .toHexString(byte.toInt())
            .takeLast(2)
            .let { if (it.length == 1) "0$it" else it }
    }

fun main() {
    val engine = engine()
    val pack = CanNode.ECU().targetSpeed.pack()
    engine(pack.toList()) {
        println(it)
    }
}
