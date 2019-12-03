package com.thermometer

private const val PacketHead = "Temp-Inner:"
private const val PacketLenMin = 33

internal sealed class TempPackage {
    object Nothing : TempPackage()
    object Failed : TempPackage()
    data class Data(val temp: Double, val humi: Double) : TempPackage()
}

/** 温度计解析器 */
internal fun engine(): (List<Byte>, (TempPackage) -> Unit) -> Unit {
    return { buffer, packet ->
        val text = String(buffer.toByteArray(), Charsets.US_ASCII)
        if (!text.startsWith(PacketHead))
            packet(TempPackage.Nothing)
        else if (text.length < PacketLenMin)
            packet(TempPackage.Failed)
        else {
            try {
                val idx1 = text.indexOf(':') + 1
                val idx2 = text.indexOf("[C]") - 1
                val idx3 = text.indexOf(',') + 1
                val idx4 = text.indexOf("[%") - 1
                val temp = text.substring(idx1, idx2).toDouble()
                val humi = text.substring(idx3, idx4).toDouble()
                packet(TempPackage.Data(temp, humi))
            }
            catch(e: Throwable) {
                packet(TempPackage.Failed)
            }
        }
    }
}
