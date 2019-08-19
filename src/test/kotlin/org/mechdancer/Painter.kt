package org.mechdancer

import org.mechdancer.remote.presets.RemoteHub
import org.mechdancer.remote.protocol.writeEnd
import org.mechdancer.remote.resources.UdpCmd
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * 画任意内容
 */
fun RemoteHub.paint(
    topic: String,
    block: ByteArrayOutputStream.() -> Unit
) {
    ByteArrayOutputStream()
        .also { stream ->
            stream.writeEnd(topic)
            stream.block()
        }
        .toByteArray()
        .let { broadcast(UdpCmd.TOPIC_MESSAGE, it) }
}

enum class FrameType(val value: Int) {
    OneFloat(0),
    OneDouble(1),
    TwoFloat(2),
    TwoDouble(3),
    ThreeFloat(4),
    ThreeDouble(5)
}

/**
 * 画一维信号
 */
fun RemoteHub.paint(
    topic: String,
    value: Double
) = paint(topic) {
    DataOutputStream(this).apply {
        writeByte(1)
        writeDouble(value)
    }
}

/**
 * 画二维信号
 */
fun RemoteHub.paint(
    topic: String,
    x: Double,
    y: Double
) = paint(topic) {
    DataOutputStream(this).apply {
        writeByte(2)
        writeDouble(x)
        writeDouble(y)
    }
}

/**
 * 画位姿信号
 */
fun RemoteHub.paint(
    topic: String,
    x: Double,
    y: Double,
    theta: Double
) = paint(topic) {
    DataOutputStream(this).apply {
        writeByte(3)
        writeDouble(x)
        writeDouble(y)
        writeDouble(theta)
    }
}

/**
 * 画单帧一维信号
 */
fun RemoteHub.paintFrame1(
    topic: String,
    list: List<Double>
) = paint(topic) {
    DataOutputStream(this).apply {
        writeByte(0)
        writeByte(FrameType.OneDouble.value)
        list.forEach(this::writeDouble)
    }
}

/**
 * 画单帧二维信号
 */
fun RemoteHub.paintFrame2(
    topic: String,
    list: List<Pair<Double, Double>>
) = paint(topic) {
    DataOutputStream(this).apply {
        writeByte(0)
        writeByte(FrameType.TwoDouble.value)
        for ((x, y) in list) {
            writeDouble(x)
            writeDouble(y)
        }
    }
}

/**
 * 画单帧位姿信号
 */
fun RemoteHub.paintFrame3(
    topic: String,
    list: List<Triple<Double, Double, Double>>
) = paint(topic) {
    DataOutputStream(this).apply {
        writeByte(0)
        writeByte(FrameType.ThreeDouble.value)
        for ((x, y, theta) in list) {
            writeDouble(x)
            writeDouble(y)
            writeDouble(theta)
        }
    }
}
