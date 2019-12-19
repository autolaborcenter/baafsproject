package com.marvelmind.mobilebeacon

import cn.autolabor.serialport.manager.Certificator
import cn.autolabor.serialport.manager.SerialPortDeviceBase
import com.marvelmind.mobilebeacon.BeaconPackage.*
import com.marvelmind.mobilebeacon.BeaconPackage.Nothing
import com.marvelmind.toIntUnsigned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.mechdancer.WatchDog
import org.mechdancer.algebra.function.vector.div
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.algebra.implement.vector.vector2DOfZero
import org.mechdancer.common.Stamped
import org.mechdancer.core.MobileBeacon
import org.mechdancer.exceptions.DataTimeoutException
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.exceptions.ExceptionMessage.Occurred
import org.mechdancer.exceptions.ExceptionMessage.Recovered
import kotlin.math.roundToInt

/**
 * Marvelmind 移动标签资源控制
 */
class SerialPortMobilBeacon
internal constructor(
    private val beaconOnMap: SendChannel<Stamped<Vector2D>>,
    private val beaconData: SendChannel<Stamped<MobileBeaconData>>,
    private val exceptions: SendChannel<ExceptionMessage>,

    portName: String?,

    private val dataTimeout: Long,

    delayLimit: Long,
    heightRange: ClosedFloatingPointRange<Double>
) : SerialPortDeviceBase(NAME, 115200, 256, portName),
    MobileBeacon {
    // 协议解析引擎
    private val engine = engine()
    // 超时异常监控
    private val dataTimeoutException =
        DataTimeoutException(NAME, dataTimeout)
    private val dataWatchDog =
        WatchDog(timeout = dataTimeout)
        { exceptions.send(Occurred(dataTimeoutException)) }
    // 数据过滤
    private val delayRange = 1..delayLimit
    private val zRange = (heightRange.start * 1000).roundToInt()..(heightRange.endInclusive * 1000).roundToInt()

    override var location = Stamped(0L, vector2DOfZero())
        private set

    private val collector = MobileBeaconDataCollector()

    private var memory = Triple(0, 0, 0)
    private fun notStatic(x: Int, y: Int, z: Int): Boolean {
        val last = memory
        memory = Triple(x, y, z)
        return memory != last
    }

    private companion object {
        const val NAME = "marvelmind mobile beacon"
    }

    override fun buildCertificator(): Certificator =
        object : CertificatorBase(dataTimeout) {
            override val activeBytes = byteArrayOf()
            override fun invoke(bytes: Iterable<Byte>): Boolean? {
                var result = false
                engine(bytes) { pack ->
                    when (pack) {
                        Nothing, Failed, is Others -> Unit
                        is Coordinate              -> result = true
                    }
                }
                return passOrTimeout(result)
            }
        }

    override fun setup(
        scope: CoroutineScope,
        toDevice: SendChannel<List<Byte>>,
        fromDevice: ReceiveChannel<List<Byte>>
    ) {
        scope.launch {
            for (bytes in fromDevice) {
//                bytes.joinToString(" ") { val s =Integer.toHexString(it.toIntUnsigned()).takeLast(2)
//                    if (s.length == 1)
//                        "0" + s
//                    else
//                        s
//                }.let { println( it) }
                val now = System.currentTimeMillis()
                engine(bytes) { pack ->
                    when (pack) {
                        Nothing, Failed, is Others -> Unit
                        is Coordinate              -> {
                            val (_, x, y, z, _, _, _, delay) = pack
                            // 过滤
                            if (pack.available
                                && delay in delayRange
                                && z in zRange
                                && notStatic(x, y, z)
                            ) {
                                location = Stamped(now - delay, vector2DOf(x, y) / 1000.0)
                                launch {
                                    beaconOnMap.send(location)
                                    exceptions.send(Recovered(dataTimeoutException))
                                }
                                dataWatchDog.feed()
                            }
                            // 发送
                            if (delay in delayRange)
                                collector.updateCoordinate(Stamped(now, pack))
                                    ?.let { launch { beaconData.send(it) } }
                        }
                        is RawDistance             ->
                            collector.updateRawDistance(Stamped(now, pack))
                                ?.let { launch { beaconData.send(it) } }
                        is Quality                 ->
                            collector.updateQuality(Stamped(now, pack))
                                ?.let { launch { beaconData.send(it) } }
                    }
                }
            }
        }
    }
}
