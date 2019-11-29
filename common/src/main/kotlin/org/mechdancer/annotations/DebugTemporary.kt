package org.mechdancer.annotations

import org.mechdancer.annotations.DebugTemporary.Operation.DELETE
import kotlin.annotation.AnnotationRetention.SOURCE
import kotlin.annotation.AnnotationTarget.*

/** 临时代码，用于调试，完成后应依 [operation] 处理 */
@Target(CLASS, FIELD, EXPRESSION, LOCAL_VARIABLE)
@Retention(SOURCE)
annotation class DebugTemporary(val operation: Operation = DELETE) {
    enum class Operation { DELETE, REDUCE, INLINE }
}
