package com.example.npuclassifier

import android.content.Context
import java.io.File

object AppLog {
    private val ring = ArrayDeque<String>(500)
    private val lock = Any()
    private var logFile: File? = null

    fun init(ctx: Context) {
        val dir = File(ctx.filesDir, "logs")
        if (!dir.exists()) dir.mkdirs()
        val f = File(dir, "app.log")
        if (f.exists() && f.length() > 1_000_000) {
            val old = File(dir, "app.log.1")
            f.renameTo(old)
        }
        logFile = f
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            e("CRASH", e.stackTraceToString())
            prev?.uncaughtException(t, e)
        }
    }

    fun d(tag: String, msg: String) { append("D", tag, msg) }
    fun e(tag: String, msg: String, t: Throwable? = null) { append("E", tag, msg + (t?.let { "\n" + it.stackTraceToString() } ?: "")) }

    private fun append(level: String, tag: String, msg: String) {
        val line = "${System.currentTimeMillis()} $level/$tag: $msg"
        synchronized(lock) {
            if (ring.size >= 500) ring.removeFirst()
            ring.addLast(line)
        }
        try { logFile?.appendText(line + "\n") } catch (e: Exception) { }
    }

    fun tail(n: Int): List<String> {
        synchronized(lock) { return ring.takeLast(n) }
    }
}