package org.mechdancer.exceptions

import org.mechdancer.SimpleLogger
import org.mechdancer.exceptions.ExceptionMessage.Occurred
import org.mechdancer.exceptions.ExceptionMessage.Recovered
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class ExceptionServer {
    private val set = mutableSetOf<RecoverableException>()
    private val logger = SimpleLogger("exceptions")
    private val lock = ReentrantReadWriteLock()

    fun isEmpty() = set.isEmpty()

    fun get() = lock.read { set.toSet() }

    fun update(exception: ExceptionMessage<*>) {
        val what = exception.what
        lock.write {
            when (exception) {
                is Occurred<*>  -> {
                    if (set.add(what)) {
                        System.err.println(what)
                        logger.log(what)
                    }
                }
                is Recovered<*> -> {
                    if (set.remove(exception.what))
                        logger.log("${what::class.java.simpleName}: recovered")
                }
            }
        }
    }
}
