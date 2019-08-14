package org.mechdancer.framework

import cn.autolabor.core.server.ServerManager
import cn.autolabor.core.server.executor.AbstractTask
import cn.autolabor.util.lambda.LambdaFunWithName
import kotlin.reflect.KCallable

// 注册任务立即调度
fun AbstractTask.dispatchNow(func: KCallable<*>, vararg params: Any?) =
    ServerManager.me().run(this, func.name, *params)

// 注册任务立即调度
fun AbstractTask.dispatchNow(func: LambdaFunWithName, vararg params: Any?) =
    ServerManager.me().run(this, func, *params)
