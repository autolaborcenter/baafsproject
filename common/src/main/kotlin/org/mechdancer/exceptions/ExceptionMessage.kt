package org.mechdancer.exceptions

sealed class ExceptionMessage
<out T : RecoverableException>(val what: T) {
    class Occurred
    <out T : RecoverableException>(what: T)
        : ExceptionMessage<T>(what)

    class Recovered
    <out T : RecoverableException>(what: T)
        : ExceptionMessage<T>(what)
}
