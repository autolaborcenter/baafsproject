package org.mechdancer.exceptions

import org.mechdancer.SimpleLogger
import org.mechdancer.exceptions.ExceptionMessage.Occurred
import org.mechdancer.exceptions.ExceptionMessage.Recovered
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class ExceptionServer {
    private val set = mutableSetOf<RecoverableException>()
    private val logger = SimpleLogger("exceptions")
    private val lock = ReentrantReadWriteLock()

    fun isEmpty() = set.isEmpty()

    fun get() = lock.read { set.toSet() }

    fun update(exception: ExceptionMessage) {
        val what = exception.what
        lock.write {
            when (exception) {
                is Occurred  -> if (set.add(what)) record(what.toString())
                is Recovered -> if (set.remove(what)) record("${what::class.java.simpleName}: recovered")
            }
        }
    }

    private fun record(msg: String) {
        System.err.println("$${SimpleDateFormat("HH:mm:ss:SSS").format(Date())} $msg")
        logger.log(msg)
    }
}
