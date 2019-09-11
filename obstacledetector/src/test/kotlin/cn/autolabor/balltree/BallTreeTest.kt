package cn.autolabor.balltree

import org.mechdancer.algebra.function.vector.DistanceType
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.vector2DOf
import org.mechdancer.paintVectors
import org.mechdancer.remote.presets.remoteHub
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis

fun main() {
    val remote = remoteHub("ball tree")
    remote.openAllNetworks()

    val random = java.util.Random()
    val vectors = List(360) {
        vector2DOf(random.nextGaussian(), random.nextGaussian())
    }

    measureTimeMillis { build(vectors.toSet(), DistanceType.Euclid.between)!! }
        .let(::println)
    val tree = build(vectors.toSet(), DistanceType.Euclid.between)!!

    thread(isDaemon = true) {
        while (true) {
            remote.paintVectors("points", vectors)
            Thread.sleep(1000)
        }
    }
    var node = tree
    val buffer = mutableListOf<Vector2D>()
    while (true) {
        val root = node.node
        val (l, r) = node.children
        buffer += root.center
        r?.node?.center?.also { buffer += it }
        buffer += root.center
        l?.node?.center?.also { buffer += it } ?: break
        node = l
        remote.paintVectors("left", buffer)
        println(buffer.size)
        Thread.sleep(1000)
    }
    println("done")
}
