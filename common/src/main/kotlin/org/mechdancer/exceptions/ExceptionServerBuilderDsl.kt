package org.mechdancer.exceptions

import org.mechdancer.BuilderDslMarker

@BuilderDslMarker
class ExceptionServerBuilderDsl private constructor() {
    private var recoverAll: () -> Unit = {}
    private var exceptionOccur: () -> Unit = {}

    fun recoverAll(block: () -> Unit) {
        recoverAll = block
    }

    fun exceptionOccur(block: () -> Unit) {
        exceptionOccur = block
    }

    companion object {
        fun exceptionServer(block: ExceptionServerBuilderDsl.() -> Unit = {}) =
            ExceptionServerBuilderDsl()
                .apply(block)
                .run {
                    ExceptionServer(recoverAll, exceptionOccur)
                }
    }
}
