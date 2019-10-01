package org.mechdancer.baafs.modules

import cn.autolabor.locator.ParticleFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.mechdancer.SimpleLogger
import org.mechdancer.baafs.modules.Default.loggers
import org.mechdancer.paint
import org.mechdancer.paintFrame2
import org.mechdancer.paintPoses
import org.mechdancer.remote.presets.RemoteHub
import java.util.concurrent.ConcurrentHashMap

/** 等待协程作用域中全部工作结束 */
fun CoroutineScope.await() {
    runBlocking { this@await.coroutineContext[Job]?.join() }
}

/** 注册步骤画图回调 */
fun ParticleFilter.registerPainter(remote: RemoteHub) {
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

fun ConcurrentHashMap<String, SimpleLogger>.getLogger(name: String): SimpleLogger =
    getOrPut(name) { SimpleLogger(name) }

/** 注册步骤日志回调 */
fun ParticleFilter.registerLogger() {
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
