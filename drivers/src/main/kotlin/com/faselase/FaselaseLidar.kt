package com.faselase

import cn.autolabor.serialport.manager.Certificator
import cn.autolabor.serialport.manager.SerialPortDeviceBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.mechdancer.WatchDog
import org.mechdancer.common.Polar
import org.mechdancer.common.Stamped
import org.mechdancer.exceptions.DataTimeoutException
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.exceptions.ExceptionMessage.Occurred
import org.mechdancer.exceptions.ExceptionMessage.Recovered
import kotlin.math.PI

/**
 * 砝石雷达资源控制
 */
internal class FaselaseLidar(
    private val exceptions: SendChannel<ExceptionMessage>,

    portName: String?,
    tag: String,

    dataTimeout: Long
) : SerialPortDeviceBase(tag, 460800, 64, portName) {
    // 解析
    private val engine = engine()
    // 缓存
    private val queue = PolarFrameCollectorQueue()

    // 访问
    val frame get() = queue.get()

    private val dataTimeoutException =
        DataTimeoutException(this.tag, dataTimeout)
    private val dataWatchDog =
        WatchDog(GlobalScope, dataTimeout)
        { exceptions.send(Occurred(dataTimeoutException)) }

    private companion object {
        val ACTIVE_BYTES = "#SF 10\r\n".toByteArray(Charsets.US_ASCII)
    }

    override fun buildCertificator(): Certificator =
        object : CertificatorBase(5000L) {
            override val activeBytes get() = ACTIVE_BYTES
            override fun invoke(bytes: Iterable<Byte>): Boolean? {
                var result = false
                engine(bytes) { result = parse(it) || result }
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
                engine(bytes) {
                    if (parse(it))
                        scope.launch { exceptions.send(Recovered(dataTimeoutException)) }
                }
        }
    }

    private var offset = .0
    private var last = .0
    private fun Double.onPeriod(): Double {
        if (this < last) offset += 2 * PI
        last = this
        return this + offset
    }

    private fun parse(pack: LidarPack) =
        when (pack) {
            LidarPack.Nothing,
            LidarPack.Failed     -> false
            is LidarPack.Invalid -> {
                val (theta) = pack
                queue.refresh(theta.onPeriod())
                false
            }
            is LidarPack.Data    -> {
                dataWatchDog.feed()
                val (rho, theta) = pack
                queue += Stamped.stamp(Polar(rho, theta.onPeriod()))
                true
            }
        }
}
