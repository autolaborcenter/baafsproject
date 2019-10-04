package org.mechdancer

import java.io.File
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

/** 简易日志器 */
class SimpleLogger(vararg names: String) {
    private val buffer = StringBuilder()
    private val name = "${names.joinToString("_")}.txt"
    private val file by lazy { File(currentLogPath.toString(), name) }

    var period = 0x40

    /** 记录一行无时间的日志 */
    infix fun logWithoutStamp(msg: String) {
        synchronized(buffer) { buffer.appendln(msg) }
        if (buffer.length >= period) flush()
    }

    /** 记录一行日志，用制表符隔开 */
    fun log(vararg msg: Any?) {
        synchronized(buffer) {
            buffer.append("${SimpleDateFormat("HH:mm:ss:SSS").format(Date())}\t")
            buffer.append("${System.currentTimeMillis()}\t")
            buffer.appendln(msg.joinToString("\t"))
        }
        if (buffer.length >= period) flush()
    }

    /** 清除缓存内容 */
    fun clear() {
        synchronized(buffer) { buffer.setLength(0) }
    }

    /** 清除日志文件内容 */
    fun forgetMemory() = file.writeText("")

    /** 缓存内容刷到文件 */
    fun flush() {
        val temp = synchronized(buffer) {
            val text = buffer.toString()
            buffer.setLength(0)
            text
        }
        thread { file.appendText(temp) }
    }

    companion object {
        // 日志
        // 运行目录下创建log文件夹
        private val logPath: String =
            File(System.getProperty("user.dir"), "log")
                .also { if (!it.exists()) it.mkdir() }
                .toPath()
                .toString()

        // log文件夹下创建本次运行的文件夹
        private val currentLogPath: Path by lazy {
            File(logPath, SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(Date()))
                .also { if (!it.exists()) it.mkdir() }
                .toPath()
        }

        inline fun <reified T> logger(name: String) =
            SimpleLogger(T::class.java.simpleName, name)
    }
}
