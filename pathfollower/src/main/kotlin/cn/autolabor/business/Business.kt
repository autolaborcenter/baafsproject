package cn.autolabor.business

import cn.autolabor.business.Business.Functions.Following
import cn.autolabor.business.Business.Functions.Recording
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.mechdancer.algebra.function.vector.euclid
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.remote.presets.RemoteHub

/** 业务模块 */
class Business internal constructor(
    private val scope: CoroutineScope,
    private val robotOnMap: ReceiveChannel<Stamped<Odometry>>,
    private val globalOnRobot: SendChannel<Pair<Sequence<Odometry>, Boolean>>,

    private val pathInterval: Double,
    localFirst: (Odometry) -> Boolean,

    painter: RemoteHub?
) {
    /** 当前业务功能 */
    var function: Functions? = null
        private set
    /** 全局路径管理 */
    val globals = PathManager(localFirst, painter)

    /** 开始录制 */
    suspend fun startRecording() {
        if (function is Recording) return
        function?.job?.cancelAndJoin()
        function = Recording(scope, robotOnMap, globals, pathInterval)
    }

    /** 开始循径 */
    suspend fun startFollowing(global: GlobalPath) {
        function?.job?.cancelAndJoin()
        function = Following(scope, robotOnMap, globalOnRobot, global)
            .apply { job.invokeOnCompletion { function = null } }
    }

    /** 取消任务 */
    suspend fun cancel() {
        function?.job?.cancelAndJoin()
        function = null
    }

    sealed class Functions {
        internal abstract val job: Job
        override fun toString(): String = javaClass.simpleName

        /** 录制路径 */
        class Recording internal constructor(
            scope: CoroutineScope,
            robotOnMap: ReceiveChannel<Stamped<Odometry>>,
            private val globals: PathManager,
            pathInterval: Double
        ) : Functions() {
            private val list = mutableListOf<Odometry>()
            override val job = scope.launch {
                for ((_, pose) in robotOnMap)
                    synchronized(list) {
                        list.lastOrNull()
                            .let { it == null || it.p euclid pose.p > pathInterval }
                            .also { if (it) list += pose }
                    }
            }

            fun save(fileName: String): Int {
                val copy = synchronized(list) { list.toList() }
                globals.save(fileName, copy)
                return copy.size
            }

            fun clear() {
                synchronized(list) { list.clear() }
            }
        }

        /** 循径 */
        class Following internal constructor(
            scope: CoroutineScope,
            private val robotOnMap: ReceiveChannel<Stamped<Odometry>>,
            private val globalOnRobot: SendChannel<Pair<Sequence<Odometry>, Boolean>>,
            val global: GlobalPath
        ) : Functions() {
            var loop = false
            override val job = scope.launch {
                for ((_, pose) in robotOnMap) {
                    val local = global[pose]
                    val completed = global.progress == 1.0
                    globalOnRobot.send(local to completed)
                    if (completed)
                        if (loop) global.progress = .0
                        else break
                }
            }
        }
    }
}
