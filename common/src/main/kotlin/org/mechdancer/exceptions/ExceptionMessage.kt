package org.mechdancer.exceptions

sealed class ExceptionMessage(val what: RecoverableException) {
    class Occurred(what: RecoverableException) : ExceptionMessage(what)
    class Recovered(what: RecoverableException) : ExceptionMessage(what)
}
