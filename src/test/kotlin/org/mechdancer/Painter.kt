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
 * 画二维单帧信号
 */
fun RemoteHub.paint(
    topic: String,
    list: List<Pair<Double, Double>>
) = paint(topic) {
    DataOutputStream(this).apply {
        writeByte(0)
        writeInt(list.size)
        writeByte(1)
        for ((x, y) in list) {
            writeDouble(x)
            writeDouble(y)
        }
    }
}
