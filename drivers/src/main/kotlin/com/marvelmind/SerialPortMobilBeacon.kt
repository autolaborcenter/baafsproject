package com.marvelmind

import cn.autolabor.serialport.manager.Certificator
import cn.autolabor.serialport.manager.SerialPortDeviceBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.mechdancer.SimpleLogger
import org.mechdancer.WatchDog
import org.mechdancer.algebra.function.vector.div
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.algebra.implement.vector.vector2DOfZero
import org.mechdancer.common.Stamped
import org.mechdancer.core.MobileBeacon
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.exceptions.ExceptionMessage.Occurred
import org.mechdancer.exceptions.ExceptionMessage.Recovered
import org.mechdancer.exceptions.device.DataTimeoutException
import kotlin.math.roundToInt

/**
 * Marvelmind 移动标签资源控制
 */
class SerialPortMobilBeacon
internal constructor(
    private val beaconOnMap: SendChannel<Stamped<Vector2D>>,
    private val exceptions: SendChannel<ExceptionMessage>,

    portName: String?,

    private val dataTimeout: Long,

    delayLimit: Long,
    heightRange: ClosedFloatingPointRange<Double>,

    private val logger: SimpleLogger?
) : SerialPortDeviceBase(NAME, 115200, 32, portName),
    MobileBeacon {
    // 协议解析引擎
    private val engine = engine()
    // 超时异常监控
    private val dataTimeoutException = DataTimeoutException(NAME, dataTimeout)
    private val dataWatchDog = WatchDog(timeout = dataTimeout) { exceptions.send(Occurred(dataTimeoutException)) }
    // 数据过滤
    private val delayRange = 1..delayLimit
    private val zRange = (heightRange.start * 1000).roundToInt()..(heightRange.endInclusive * 1000).roundToInt()

    override var location = Stamped(0L, vector2DOfZero())
        private set

    private var memory = Triple(0, 0, 0)
    private fun notStatic(x: Int, y: Int, z: Int): Boolean {
        val last = memory
        memory = Triple(x, y, z)
        return memory != last
    }

    override fun buildCertificator(): Certificator =
        object : CertificatorBase(dataTimeout) {
            override val activeBytes = byteArrayOf()
            override fun invoke(bytes: Iterable<Byte>): Boolean? {
                var result = false
                engine(bytes) { pack ->
                    when (pack) {
                        is BeaconPackage.Nothing -> logger?.log("nothing")
                        is BeaconPackage.Failed  -> logger?.log("failed")
                        is BeaconPackage.Data    -> result = GlobalScope.parse(pack) || result
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
            for (bytes in fromDevice)
                engine(bytes) { pack ->
                    when (pack) {
                        is BeaconPackage.Nothing -> logger?.log("nothing")
                        is BeaconPackage.Failed  -> logger?.log("failed")
                        is BeaconPackage.Data    -> parse(pack)
                    }
                }
        }
    }

    private fun CoroutineScope.parse(
        pack: BeaconPackage.Data
    ): Boolean {
        val (code, payload) = pack
        return if (code != COORDINATE_CODE) {
            logger?.log("code = $code")
            false
        } else {
            val now = System.currentTimeMillis()
            val value = ResolutionCoordinate(payload)
            val x = value.x
            val y = value.y
            val z = value.z
            val delay = value.delay
            logger?.log("delay = $delay, x = ${x / 1000.0}, y = ${y / 1000.0}, z = ${z / 1000.0}")
            // 过滤
            if (delay in delayRange && z in zRange && notStatic(x, y, z)) {
                location = Stamped(now - delay, vector2DOf(x, y) / 1000.0)
                launch {
                    beaconOnMap.send(location)
                    exceptions.send(Recovered(dataTimeoutException))
                }
                dataWatchDog.feed()
                true
            } else
                false
        }
    }

    // 常量参数
    private companion object {
        const val NAME = "marvelmind mobile beacon"
        const val COORDINATE_CODE = 0x11
    }
}
