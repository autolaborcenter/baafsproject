package cn.autolabor.balltree

import cn.autolabor.balltree.Tree.*
import cn.autolabor.balltree.Tree.Companion.build
import org.mechdancer.algebra.function.vector.DistanceType
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
    val buffer = mutableListOf(node.value)
    loop@ while (true) {
        when (node) {
            is SingleBranch -> {
                buffer += node.child.value
                node = node.child
            }
            is DoubleBranch -> {
                buffer += node.right.value
                buffer += node.value
                buffer += node.left.value
                node = node.left
            }
            is Leaf         -> break@loop
            else            -> break@loop
        }
        remote.paintVectors("left", buffer)
        Thread.sleep(1000)
    }
    println("done")
}
