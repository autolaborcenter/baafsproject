package com.thermometer

import com.thermometer.TemperXDsl.Companion.startTemperX
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.mechdancer.channel
import org.mechdancer.common.Stamped
import org.mechdancer.exceptions.ExceptionMessage

// 测试温湿度计，每秒读取一次
fun main() = runBlocking<Unit>(Dispatchers.Default) {
    // 话题
    val exceptions = channel<ExceptionMessage>()
    val therm = channel<Stamped<Pair<Double, Double>>>()
    // 任务
    startTemperX(
        therm = therm,
        exceptions = exceptions
    ) {
        port = "COM14"
    }
    var start = System.currentTimeMillis()
    launch {
        for ((stamp, p) in therm) {
            println("dt = ${stamp - start}, temp = ${String.format("%.2f", p.first)} [C], humi = ${String.format("%.2f", p.second)} [%]")
            start = stamp
        }
    }
    launch {
        for (e in exceptions) {
            if (e is ExceptionMessage.Occurred) {
                println(e.what)
            }
        }
    }
}