package org.mechdancer

/** 施密特触发器 */
class Schmitt<T>(
    private val positive: (T) -> Boolean,
    private val negative: (T) -> Boolean
) {
    private var state = false

    fun update(value: T) =
        when {
            state -> !negative(value)
            else  -> positive(value)
        }.also { state = it }
}
