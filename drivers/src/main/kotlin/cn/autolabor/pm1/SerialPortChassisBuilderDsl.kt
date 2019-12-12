package cn.autolabor.pm1

import cn.autolabor.serialport.manager.SerialPortManager
import kotlinx.coroutines.channels.SendChannel
import org.mechdancer.annotations.BuilderDslMarker
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.geometry.angle.Angle
import org.mechdancer.geometry.angle.toDegree
import org.mechdancer.geometry.angle.toRad

@BuilderDslMarker
class SerialPortChassisBuilderDsl private constructor() {
    var portName: String? = null

    var wheelEncodersPulsesPerRound: Int = 4 * 400 * 20
    var rudderEncoderPulsesPerRound: Int = 16384

    var width: Double = .465
    var leftRadius: Double = .105
    var rightRadius: Double = .105
    var length: Double = .355

    var odometryInterval: Long = 40L
    var maxWheelSpeed: Angle = 10.toRad()
    var maxV: Double = 1.1
    var maxW: Angle = 45.toDegree()
    var optimizeWidth: Angle = 45.toDegree()
    var maxAccelerate: Double = 1.1

    var retryInterval: Long = 500L

    companion object {
        fun SerialPortManager.registerPM1Chassis(
            robotOnOdometry: SendChannel<Stamped<Odometry>>,
            block: SerialPortChassisBuilderDsl.() -> Unit = {}
        ) =
            SerialPortChassisBuilderDsl()
                .apply(block)
                .apply {
                    require(wheelEncodersPulsesPerRound > 0)
                    require(rudderEncoderPulsesPerRound > 0)

                    require(width > 0)
                    require(leftRadius > 0)
                    require(rightRadius > 0)
                    require(length > 0)

                    require(odometryInterval > 0)
                    require(maxWheelSpeed.value > 0)
                    require(maxV > 0)
                    require(maxW.value > 0)
                    require(optimizeWidth.asDegree() in 0.0..90.0)
                    require(maxAccelerate > 0)

                    require(retryInterval > 0)
                }
                .run {
                    SerialPortChassis(
                        robotOnOdometry = robotOnOdometry,

                        portName = portName,

                        wheelEncodersPulsesPerRound = wheelEncodersPulsesPerRound,
                        rudderEncoderPulsesPerRound = rudderEncoderPulsesPerRound,

                        width = width,
                        leftRadius = leftRadius,
                        rightRadius = rightRadius,
                        length = length,

                        odometryInterval = odometryInterval,
                        maxWheelSpeed = maxWheelSpeed,
                        maxV = maxV,
                        maxW = maxW,
                        optimizeWidth = optimizeWidth,
                        maxAccelerate = maxAccelerate)
                }
                .also(this::register)
    }
}
