package org.mechdancer.framework

import cn.autolabor.core.server.executor.AbstractTask
import cn.autolabor.core.server.message.MessageHandle
import cn.autolabor.module.communication.TCPClientSupport
import cn.autolabor.module.communication.TCPRequest
import cn.autolabor.module.communication.TCPRespStatusType.SUCCESS
import cn.autolabor.util.Strings
import kotlin.reflect.KCallable

/**
 * 进行全局设置
 * @return 全局设置实际执行的时间
 */
//private val settingOnce =
//    RunOnce<GlobalSettingsDsl.() -> Unit, Long> {
//        GlobalSettingsDsl().apply(it).build()
//        System.currentTimeMillis()
//    }

/**
 *
 */
//fun global(block: GlobalSettingsDsl.() -> Unit) =
//    settingOnce(block)
//        ?.let { System.currentTimeMillis() - it < 5 }

// 随机 ID
fun GlobalSettingsDsl.randomId(vararg prefix: String) {
    id = "${prefix.joinToString("_")}_${Strings.getShortUUID()}"
}

// 消息读写
var <T> MessageHandle<T>?.data: T?
    get() = this?.firstData
    set(value) {
        this?.pushSubData(value)
    }

// TCP 调用
fun call(
    device: String,
    task: Class<out AbstractTask>,
    method: KCallable<*>,
    vararg params: Any?
): Any? =
    TCPRequest(task.simpleName, method.name)
        .apply { params.forEach { addParam(it) } }
        .let { TCPClientSupport.callOne(device, it) }
        .also { response ->
            response.status
                .takeIf { it != SUCCESS }
                ?.toString()
                ?.let { throw RuntimeException(it) }
        }
        .result
