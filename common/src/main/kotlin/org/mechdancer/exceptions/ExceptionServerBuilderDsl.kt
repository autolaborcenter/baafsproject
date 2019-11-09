package org.mechdancer.exceptions

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
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
                .run { ExceptionServer(recoverAll, exceptionOccur) }

        fun CoroutineScope.startExceptionServer(
            exceptions: ReceiveChannel<ExceptionMessage>,
            block: ExceptionServerBuilderDsl.() -> Unit = {}
        ) =
            exceptionServer(block).also { server ->
                launch {
                    for (exception in exceptions)
                        server.update(exception)
                }
            }
    }
}
