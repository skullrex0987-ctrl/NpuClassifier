package com.example.npuclassifier

import android.content.Context
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class ResultSink(private val ctx: Context) {
    private val q = LinkedBlockingQueue<SortItem>()
    private val buf = mutableListOf<SortItem>()
    private var closed = false
    private val writer = Thread {
        while (true) {
            val first = q.poll(500, TimeUnit.MILLISECONDS)
            synchronized(buf) {
                if (first != null) {
                    buf.add(first)
                    q.drainTo(buf, 99)
                }
                if (buf.isNotEmpty() && (first == null || buf.size >= 100)) {
                    try {
                        Repo.saveResults(ctx, buf.toList())
                    } catch (e: Exception) {
                        AppLog.e("ResultSink", "saveResults failed", e)
                    }
                    buf.clear()
                }
                if (closed && q.isEmpty() && buf.isEmpty()) return@Thread
            }
        }
    }.apply { name = "db-writer"; isDaemon = true; start() }

    fun add(item: SortItem) {
        q.put(item)
    }

    fun close() {
        closed = true
        writer.join()
    }
}