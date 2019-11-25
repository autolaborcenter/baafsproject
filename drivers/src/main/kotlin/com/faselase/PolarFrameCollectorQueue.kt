package com.faselase

import org.mechdancer.common.Polar
import org.mechdancer.common.Stamped
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.PI

/** 极坐标分帧队列（线程安全） */
class PolarFrameCollectorQueue {
    private val queue: Queue<Stamped<Polar>> = LinkedList<Stamped<Polar>>()
    private val lock = ReentrantReadWriteLock()

    fun get(): List<Stamped<Polar>> = lock.read { queue.toList() }

    private fun innerRefresh(theta: Double) {
        val head = theta - 2 * PI
        while (queue.peek()?.data?.angle?.let { it < head } == true) queue.poll()
    }

    fun refresh(theta: Double) {
        lock.write { innerRefresh(theta) }
    }

    operator fun plusAssign(data: Stamped<Polar>) {
        lock.write {
            innerRefresh(data.data.angle)
            queue.offer(data)
        }
    }
}
