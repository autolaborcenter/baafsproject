package cn.autolabor

import java.io.Closeable

interface Resource : Closeable {
    val resourceName:String

    operator fun invoke()
}
