package org.mechdancer.framework

import cn.autolabor.core.server.ServerManager
import cn.autolabor.core.server.executor.AbstractTask
import kotlin.reflect.KCallable

// 获取特定任务组的引用
inline fun <reified T : AbstractTask> getTask(): T? =
    ServerManager.me().getTaskByName(T::class.java.simpleName) as? T

// 注册任务集
fun start(task: AbstractTask) {
    ServerManager.me().register(task)
}

// 设置参数
operator fun AbstractTask.set(name: String, value: Any) {
    ServerManager.me().setConfig(this, name, value)
}

// 添加启动任务
fun AbstractTask.addRegisterTaskCallback(func: KCallable<*>) =
    ServerManager.me().addRegisterTaskCallback(this, func.name)

// 清除启动任务
fun AbstractTask.removeRegisterTaskCallback(func: KCallable<*>) =
    ServerManager.me().removeRegisterTaskCallback(this, func.name)
