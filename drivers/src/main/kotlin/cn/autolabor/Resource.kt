package cn.autolabor

import java.io.Closeable

interface Resource : Closeable {
    operator fun invoke()
}
