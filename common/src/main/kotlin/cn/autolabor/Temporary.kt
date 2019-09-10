package cn.autolabor

import cn.autolabor.Temporary.Operation.DELETE
import kotlin.annotation.AnnotationRetention.SOURCE
import kotlin.annotation.AnnotationTarget.*

/** 临时代码，用于调试，完成后应依 [operation] 处理 */
@Target(CLASS, FIELD, EXPRESSION)
@Retention(SOURCE)
annotation class Temporary(val operation: Operation = DELETE) {
    enum class Operation { DELETE, REDUCE, INLINE }
}
