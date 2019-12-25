package cn.autolabor.baafs.bussiness

import cn.autolabor.baafs.bussiness.Business.Functions.Following
import cn.autolabor.baafs.bussiness.Business.Functions.Recording
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.mechdancer.algebra.function.vector.euclid
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.core.LocalPath
import org.mechdancer.global.GlobalPathPlanner

/** 业务模块 */
class Business internal constructor(
    private val scope: CoroutineScope,
    private val robotOnMap: ReceiveChannel<Stamped<Odometry>>,
    private val globalOnRobot: SendChannel<LocalPath>,

    private val pathInterval: Double,
    localFirst: (Odometry) -> Boolean
) {
    /** 当前业务功能 */
    var function: Functions? = null
        private set
    /** 全局路径管理 */
    private val globals = PathManager(localFirst)

    /** 开始录制 */
    suspend fun startRecording() {
        if (function is Recording) return
        function?.job?.cancelAndJoin()
        function = Recording(scope, robotOnMap, globals, pathInterval)
    }

    private suspend fun follow(name: String, get: PathManager.() -> GlobalPathPlanner?) {
        val current = function
        if (current is Following && current.pathName == name) {
            current.planner.progress = .0
            return
        }
        current?.job?.cancelAndJoin()
        val global = globals.get()
            ?: throw IllegalArgumentException("no path named \"$name\"")
        function = Following(scope, robotOnMap, globalOnRobot, name, global)
            .apply { job.invokeOnCompletion { function = null } }
    }

    /** 开始循径 */
    suspend fun startFollowing(name: String) {
        follow(name) { load(name, .0) }
    }

    /** 开始循径 */
    suspend fun resumeFollowing(name: String) {
        follow(name) { resume(name) }
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
                list += robotOnMap.receive().data
                for ((_, pose) in robotOnMap)
                    synchronized(list) {
                        list.lastOrNull()
                            ?.takeIf { it.p euclid pose.p > pathInterval }
                            ?.also { list += pose }
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
            private val globalOnRobot: SendChannel<LocalPath>,
            val pathName: String,
            val planner: GlobalPathPlanner
        ) : Functions() {
            override val job = scope.launch {
                for ((_, pose) in robotOnMap) {
                    val local = planner.plan(pose)
                    globalOnRobot.send(local)
                    if (local == LocalPath.Finish) break
                }
            }
        }
    }
}
