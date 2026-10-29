package com.example.npuclassifier

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

class SortEngine(private val ctx: Context) {

    companion object {
        fun decide(conf: Float, gap: Float, cfg: SortSettings): Status =
            if (conf >= cfg.threshold && gap >= cfg.minGap) Status.AUTO else Status.REVIEW

        fun prettyLabel(raw: String): String =
            raw.substringBefore(',').trim().split(" ")
                .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    }

    /**
     * Processes [items] on every selected backend IN PARALLEL: each backend gets its own
     * single thread + interpreter and pulls from one shared queue, so faster backends do more.
     */
    suspend fun run(
        items: List<MediaItem>,
        cfg: SortSettings,
        cats: List<Category>,
        onItem: (SortItem) -> Unit,
        onBackendError: (Backend, String) -> Unit
    ) {
        val queue = ConcurrentLinkedQueue(items)
        val backends: Set<Backend> = if (cfg.backends.isEmpty()) setOf(Backend.CPU) else cfg.backends
        coroutineScope {
            backends.map { b ->
                val ex = Executors.newSingleThreadExecutor()
                val job = async(ex.asCoroutineDispatcher()) { worker(b, queue, cfg, onItem, onBackendError) }
                job.invokeOnCompletion { ex.shutdown() }
                job
            }.awaitAll()
        }
        // If every chosen backend failed to start, fall back to CPU so nothing is left unprocessed.
        if (queue.isNotEmpty() && Backend.CPU !in backends) {
            val ex = Executors.newSingleThreadExecutor()
            try {
                withContext(ex.asCoroutineDispatcher()) { worker(Backend.CPU, queue, cfg, onItem, onBackendError) }
            } finally {
                ex.shutdown()
            }
        }
    }

    private fun CoroutineScope.worker(
        b: Backend,
        queue: ConcurrentLinkedQueue<MediaItem>,
        cfg: SortSettings,
        onItem: (SortItem) -> Unit,
        onErr: (Backend, String) -> Unit
    ) {
        val clf = try {
            Classifier(ctx, b)
        } catch (e: Throwable) {
            onErr(b, e.message ?: e.javaClass.simpleName)
            return
        }
        try {
            while (isActive) {
                val m = queue.poll() ?: break
                val t0 = SystemClock.elapsedRealtime()
                val si = try {
                    process(clf, m, cfg, b)
                } catch (e: Throwable) {
                    SortItem(m, status = Status.ERROR, note = e.message ?: "error", backend = b)
                }
                onItem(si.copy(ms = SystemClock.elapsedRealtime() - t0))
            }
        } finally {
            clf.close()
        }
    }

    private fun process(clf: Classifier, m: MediaItem, cfg: SortSettings, b: Backend): SortItem {
        val scores = if (m.isVideo) videoScores(clf, m, cfg) else clf.scores(decode(m))
        val order = scores.indices.sortedByDescending { scores[it] }.take(3)
        val top = order.map { (clf.labels.getOrNull(it) ?: "class $it") to scores[it] }
        val conf = top[0].second
        val gap = conf - (top.getOrNull(1)?.second ?: 0f)
        return SortItem(
            media = m, label = prettyLabel(top[0].first), conf = conf, gap = gap, top = top,
            status = decide(conf, gap, cfg), backend = b
        )
    }

    private fun decode(m: MediaItem): Bitmap {
        val src = if (m.path != null) ImageDecoder.createSource(File(m.path))
        else ImageDecoder.createSource(ctx.contentResolver, m.uri)
        return ImageDecoder.decodeBitmap(src) { dec, info, _ ->
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val mx = maxOf(info.size.width, info.size.height)
            if (mx > 512) { // classifier input is small; decoding smaller is much faster
                val f = 512f / mx
                dec.setTargetSize(maxOf(1, (info.size.width * f).toInt()), maxOf(1, (info.size.height * f).toInt()))
            }
        }
    }

    /** Samples frames evenly, classifies each, and averages the scores (a "vote"). */
    private fun videoScores(clf: Classifier, m: MediaItem, cfg: SortSettings): FloatArray {
        val r = MediaMetadataRetriever()
        try {
            if (m.path != null) r.setDataSource(m.path) else r.setDataSource(ctx, m.uri)
            val durMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val byInterval = (durMs / (cfg.frameIntervalSec * 1000L)).toInt().coerceAtLeast(1)
            val n = minOf(cfg.maxFrames, byInterval)
            var acc: FloatArray? = null
            var cnt = 0
            for (i in 0 until n) {
                val tUs = durMs * 1000L * (2 * i + 1) / (2 * n) // centre of each segment
                val bmp = r.getFrameAtTime(tUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: continue
                val s = clf.scores(bmp)
                if (acc == null) acc = FloatArray(s.size)
                for (k in s.indices) acc[k] += s[k]
                cnt++
            }
            val a = requireNotNull(acc) { "Could not read video frames" }
            for (k in a.indices) a[k] = a[k] / cnt
            return a
        } finally {
            r.release()
        }
    }

    /** Classify with NPU (fallback CPU), return fingerprints. */
    suspend fun embedFiles(items: List<MediaItem>): List<FloatArray?> {
        // Stub - Agent A will implement
        return items.map { null }
    }
}
