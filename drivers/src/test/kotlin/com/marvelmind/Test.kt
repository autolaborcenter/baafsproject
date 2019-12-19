package com.marvelmind

import cn.autolabor.serialport.manager.SerialPortManager
import com.marvelmind.mobilebeacon.MobileBeaconData
import com.marvelmind.mobilebeacon.SerialPortMobileBeaconBuilderDsl.Companion.registerMobileBeacon
import com.marvelmind.modem.SerialPortModemBuilderDsl.Companion.registerModem
import com.thermometer.Humiture
import com.thermometer.SerialPortTemperXBuilderDsl.Companion.registerTemperX
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.channel
import org.mechdancer.common.Stamped
import org.mechdancer.exceptions.ExceptionMessage

@ObsoleteCoroutinesApi
fun main() = runBlocking {
    // 话题
    val exceptions = channel<ExceptionMessage>()
    val beaconOnMap = channel<Stamped<Vector2D>>()
    val beaconData = channel<Stamped<MobileBeaconData>>()
    val temperatures = channel<Stamped<Humiture>>()
    // 连接串口外设
    val manager = SerialPortManager(exceptions)
    // 配置温度计
    manager.registerTemperX(
        temperatures = temperatures,
        exceptions = exceptions
    ) {
        period = 1000L
    }
    // 配置定位标签
    manager.registerMobileBeacon(
        beaconOnMap = beaconOnMap,
        beaconData = beaconData,
        exceptions = exceptions
    ) {
        portName = "COM25"
        dataTimeout = 5000L
        delayLimit = 400L
    }
    // 配置路由
    manager.registerModem(
        humitures = temperatures,
        hedgehog = beaconData
    ) {
        portName = "COM3"
        hedgeIdList = ByteArray(1) { 15 }
    }
    // 连接串口设备
    sync@ while (true) {
        val remain = manager.sync()
        when {
            remain.isEmpty() -> {
                println("Every devices are ready.")
                break@sync
            }
            else             -> {
                println("There are still $remain devices offline, press ENTER to sync again.")
                readLine()
            }
        }
    }
    for ((_, _) in beaconOnMap);
}
