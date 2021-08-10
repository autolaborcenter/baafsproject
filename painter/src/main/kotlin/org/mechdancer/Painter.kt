package org.mechdancer

import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.common.Odometry
import org.mechdancer.common.shape.Polygon
import org.mechdancer.dependency.must
import org.mechdancer.remote.presets.RemoteHub
import org.mechdancer.remote.protocol.writeEnd
import org.mechdancer.remote.resources.Command
import org.mechdancer.remote.resources.MulticastSockets
import org.mechdancer.remote.resources.Name
import org.mechdancer.remote.resources.Networks
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/** 生成网络连接信息字符串 */
fun RemoteHub.networksInfo() =
    with(components) {
        "${must<Name>().field} opened ${must<Networks>().view.size} networks on ${must<MulticastSockets>().address}"
    }

private const val DIR_MASK = 0b0100
private const val FRAME_MASK = 0b1000

private object PaintCommand : Command {
    override val id = 6.toByte()
}

// 画任意内容
private fun RemoteHub.paint(
    topic: String,
    byte: Int,
    block: ByteArrayOutputStream.() -> Unit
) {
    ByteArrayOutputStream()
        .also { stream ->
            stream.writeEnd(topic)
            stream.write(byte)
            stream.block()
        }
        .toByteArray()
        .let { broadcast(PaintCommand, it) }
}

/**
 * 画一维信号
 */
fun RemoteHub.paint(
    topic: String,
    value: Number
) = paint(topic, 1) {
    DataOutputStream(this).apply {
        writeFloat(value.toFloat())
    }
}

/**
 * 画二维信号
 */
fun RemoteHub.paint(
    topic: String,
    x: Number,
    y: Number
) = paint(topic, 2) {
    DataOutputStream(this).apply {
        writeFloat(x.toFloat())
        writeFloat(y.toFloat())
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
) = paint(topic, 2 or DIR_MASK) {
    DataOutputStream(this).apply {
        writeFloat(x.toFloat())
        writeFloat(y.toFloat())
        writeFloat(theta.toFloat())
    }
}

/** 画位姿信号 */
fun RemoteHub.paintPose(
    topic: String,
    pose: Odometry
) = paint(topic, 2 or DIR_MASK) {
    DataOutputStream(this).apply {
        writeFloat(pose.p.x.toFloat())
        writeFloat(pose.p.y.toFloat())
        writeFloat(pose.d.asRadian().toFloat())
    }
}

/**
 * 画二维信号
 */
fun RemoteHub.paintFrame2(
    topic: String,
    list: Iterable<Pair<Number, Number>>
) = paint(topic, 2 or FRAME_MASK) {
    DataOutputStream(this).apply {
        for ((x, y) in list) {
            writeFloat(x.toFloat())
            writeFloat(y.toFloat())
        }
        writeFloat(Float.NaN)
        writeFloat(Float.NaN)
    }
}

/**
 * 画单帧位姿信号
 */
fun RemoteHub.paintFrame3(
    topic: String,
    list: List<Triple<Double, Double, Double>>
) = paint(topic, 2 or DIR_MASK or FRAME_MASK) {
    DataOutputStream(this).apply {
        for ((x, y, theta) in list) {
            writeFloat(x.toFloat())
            writeFloat(y.toFloat())
            writeFloat(theta.toFloat())
        }
        writeFloat(Float.NaN)
        writeFloat(Float.NaN)
    }
}

/**
 * 画单帧位姿信号
 */
fun RemoteHub.paintPoses(
    topic: String,
    list: List<Odometry>
) = paint(topic, 2 or DIR_MASK or FRAME_MASK) {
    DataOutputStream(this).apply {
        for ((p, d) in list) {
            writeFloat(p.x.toFloat())
            writeFloat(p.y.toFloat())
            writeFloat(d.asRadian().toFloat())
        }
        writeFloat(Float.NaN)
        writeFloat(Float.NaN)
    }
}

/**
 * 画单帧向量信号
 */
fun RemoteHub.paintVectors(
    topic: String,
    list: Collection<Vector2D>
) = paint(topic, 2 or FRAME_MASK) {
    DataOutputStream(this).apply {
        for ((x, y) in list) {
            writeFloat(x.toFloat())
            writeFloat(y.toFloat())
        }
        writeFloat(Float.NaN)
        writeFloat(Float.NaN)
    }
}

fun RemoteHub.paint(
    topic: String,
    shape: Polygon
) = paint(topic, 2 or FRAME_MASK) {
    DataOutputStream(this).apply {
        for ((x, y) in shape.vertex) {
            writeFloat(x.toFloat())
            writeFloat(y.toFloat())
        }
        with(shape.vertex.first()) {
            writeFloat(x.toFloat())
            writeFloat(y.toFloat())
        }
        writeFloat(Float.NaN)
        writeFloat(Float.NaN)
    }
}
