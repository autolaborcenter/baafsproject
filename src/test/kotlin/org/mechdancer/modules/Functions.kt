package org.mechdancer.modules

import cn.autolabor.locator.ParticleFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.mechdancer.SimpleLogger
import org.mechdancer.modules.Default.loggers
import org.mechdancer.paint
import org.mechdancer.paintFrame2
import org.mechdancer.paintPoses
import org.mechdancer.remote.presets.RemoteHub
import java.util.concurrent.ConcurrentHashMap

fun CoroutineScope.await() {
    runBlocking { this@await.coroutineContext[Job]?.join() }
}

fun ParticleFilter.paintWith(remote: RemoteHub) {
    synchronized(stepFeedback) {
        stepFeedback += { (measureWeight, particleWeight, _, _, eLocator, _) ->
            with(remote) {
                paintPoses("粒子群", particles.map { it.first })
                paint("定位权重", measureWeight)
                paint("粒子权重", particleWeight)
                paint("粒子滤波（定位标签）", eLocator.p.x, eLocator.p.y, eLocator.d.asRadian())
                paintFrame2("粒子寿命", particles.mapIndexed { i, (_, n) -> i.toDouble() to n.toDouble() })
            }
        }
    }
}

private fun ConcurrentHashMap<String, SimpleLogger>.getLogger(name: String) =
    getOrPut(name) { SimpleLogger(name) }

fun ParticleFilter.log() {
    synchronized(stepFeedback) {
        stepFeedback += { (measureWeight, particleWeight, _, _, eLocator, _) ->
            with(loggers) {
                getLogger("定位权重").log(measureWeight)
                getLogger("粒子权重").log(particleWeight)
                getLogger("粒子滤波（定位标签）").log(eLocator.p.x, eLocator.p.y, eLocator.d.asRadian())
            }
        }
    }
}
