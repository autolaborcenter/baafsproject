package cn.autolabor

import java.io.Closeable

interface Resource : Closeable {
    val info: String

    operator fun invoke()
}
