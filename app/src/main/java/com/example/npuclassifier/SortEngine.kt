package com.example.npuclassifier

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
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

    suspend fun run(
        items: List<MediaItem>,
        cfg: SortSettings,
        cats: List<Category>,
        onItem: (SortItem) -> Unit,
        onBackendError: (Backend, String) -> Unit
    ) {
        val filtered = items.filter { m ->
            if (m.isVideo) true else {
                val sizeKb = m.path?.let { File(it).length() / 1024 } ?: 0L
                sizeKb >= cfg.minFileKb
            }
        }
        val queue = ConcurrentLinkedQueue(filtered)
        val backends: Set<Backend> = if (cfg.backends.isEmpty()) setOf(Backend.CPU) else cfg.backends
        val allowedBackends = if (cfg.model == "clip") backends.filter { it == Backend.CPU || it == Backend.NPU }.toSet() else backends
        val finalBackends = if (allowedBackends.isEmpty()) setOf(Backend.CPU) else allowedBackends
        coroutineScope {
            finalBackends.map { b ->
                val ex = Executors.newSingleThreadExecutor()
                val job = async(ex.asCoroutineDispatcher()) { worker(b, queue, cfg, cats, onItem, onBackendError) }
                job.invokeOnCompletion { ex.shutdown() }
                job
            }.awaitAll()
        }
        if (queue.isNotEmpty() && Backend.CPU !in finalBackends) {
            val ex = Executors.newSingleThreadExecutor()
            try {
                withContext(ex.asCoroutineDispatcher()) { worker(Backend.CPU, queue, cfg, cats, onItem, onBackendError) }
            } finally {
                ex.shutdown()
            }
        }
    }

    private suspend fun CoroutineScope.worker(
        b: Backend,
        queue: ConcurrentLinkedQueue<MediaItem>,
        cfg: SortSettings,
        cats: List<Category>,
        onItem: (SortItem) -> Unit,
        onErr: (Backend, String) -> Unit
    ) {
        try {
            while (isActive) {
                val m = queue.poll() ?: break
                val t0 = SystemClock.elapsedRealtime()
                val si = try {
                    processItem(m, cfg, cats, b)
                } catch (e: Throwable) {
                    SortItem(m, status = Status.ERROR, note = e.message ?: "error", backend = b)
                }
                onItem(si.copy(ms = SystemClock.elapsedRealtime() - t0))
            }
        } catch (e: Throwable) {
            onErr(b, e.message ?: e.javaClass.simpleName)
        }
    }

    private suspend fun processItem(m: MediaItem, cfg: SortSettings, cats: List<Category>, b: Backend): SortItem {
        if (m.isVideo) {
            val frameItems = mutableListOf<SortItem>()
            VideoAnalyzer.forEachFrame(ctx, m, cfg) { bmp ->
                val item = processImage(bmp, m, cfg, cats, b, isFrame = true)
                frameItems.add(item)
            }
            val videoItem = SortItem(media = m, frameItems = frameItems, backend = b)
            return Decision.apply(videoItem, cats, cfg, candidates(cfg, cats))
        }

        val sizeKb = m.path?.let { File(it).length() / 1024 } ?: 0L
        if (sizeKb < cfg.minFileKb) {
            return SortItem(m, status = Status.REVIEW, note = "too small", backend = b)
        }

        val item = processImage(null, m, cfg, cats, b, isFrame = false)
        return Decision.apply(item, cats, cfg, candidates(cfg, cats))
    }

    private fun processImage(bmp: Bitmap?, m: MediaItem, cfg: SortSettings, cats: List<Category>, b: Backend, isFrame: Boolean): SortItem {
        val bitmap = bmp ?: decode(m)
        val shouldRecycle = bmp == null

        return try {
            if (cfg.model == "clip") {
                val embedder = ClipEmbedder(ctx, b)
                try {
                    val imgEmb = embedder.embedImage(bitmap)
                    SortItem(
                        media = m,
                        emb = imgEmb,
                        backend = b
                    )
                } finally {
                    embedder.close()
                }
            } else {
                val clf = Classifier(ctx, b)
                try {
                    val scores = clf.scores(bitmap)
                    val order = scores.indices.sortedByDescending { scores[it] }.take(3)
                    val top = order.map { (clf.labels.getOrNull(it) ?: "class $it") to scores[it] }
                    val conf = top[0].second
                    val gap = conf - (top.getOrNull(1)?.second ?: 0f)
                    SortItem(
                        media = m,
                        label = prettyLabel(top[0].first),
                        conf = conf,
                        gap = gap,
                        top = top,
                        status = decide(conf, gap, cfg),
                        backend = b,
                        emb = Emb.fromScores(scores)
                    )
                } finally {
                    clf.close()
                }
            }
        } finally {
            if (shouldRecycle) bitmap.recycle()
        }
    }

    private fun decode(m: MediaItem): Bitmap {
        val src = if (m.path != null) ImageDecoder.createSource(File(m.path))
        else ImageDecoder.createSource(ctx.contentResolver, m.uri)
        return ImageDecoder.decodeBitmap(src) { dec, info, _ ->
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val minSide = minOf(w, h)
            var n = 1
            while (minSide / (n * 2) >= 256) n *= 2
            if (n > 1) dec.setTargetSampleSize(n)
        }
    }

    suspend fun embedFiles(items: List<MediaItem>, cfg: SortSettings): List<FloatArray?> {
        return items.map { m ->
            try {
                if (cfg.model == "clip") {
                    val embedder = ClipEmbedder(ctx, Backend.CPU)
                    try {
                        val bmp = decode(m)
                        val emb = embedder.embedImage(bmp)
                        bmp.recycle()
                        emb
                    } finally {
                        embedder.close()
                    }
                } else {
                    val clf = Classifier(ctx, Backend.CPU)
                    try {
                        val scores = clf.scores(decode(m))
                        Emb.fromScores(scores)
                    } finally {
                        clf.close()
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun ensureExamples(cfg: SortSettings, cats: List<Category>): List<Category> {
        return cats // simplified
    }

    suspend fun candidates(cfg: SortSettings, cats: List<Category>): List<Candidate> {
        if (cfg.model != "clip") return emptyList()
        val cands = mutableListOf<Candidate>()
        if (cfg.useMine) {
            for (cat in cats) {
                val prompt = if (cat.prompt.isNotEmpty()) cat.prompt else "a photo of ${cat.name}"
                val embedder = ClipEmbedder(ctx, Backend.CPU)
                try {
                    val textEmb = embedder.embedText(prompt)
                    if (textEmb != null) {
                        cands.add(Candidate(cat.name, "mine", textEmb))
                    }
                } finally {
                    embedder.close()
                }
            }
        }
        if (cfg.useBuiltin) {
            val embedder = ClipEmbedder(ctx, Backend.CPU)
            try {
                for (label in BuiltinLabels.CLIP) {
                    val textEmb = embedder.embedText(label)
                    if (textEmb != null) {
                        cands.add(Candidate(label, "builtin", textEmb))
                    }
                }
            } finally {
                embedder.close()
            }
        }
        return cands
    }

    suspend fun speedTest(items: List<MediaItem>, cfg: SortSettings): Map<Backend, Double> {
        val testItems = items.take(20)
        val result = mutableMapOf<Backend, Double>()
        for (b in cfg.backends) {
            if (cfg.model == "clip" && b == Backend.GPU) continue
            val times = mutableListOf<Long>()
            for (m in testItems) {
                val t0 = SystemClock.elapsedRealtime()
                try {
                    if (cfg.model == "clip") {
                        val embedder = ClipEmbedder(ctx, b)
                        try {
                            val bmp = decode(m)
                            embedder.embedImage(bmp)
                            bmp.recycle()
                        } finally {
                            embedder.close()
                        }
                    } else {
                        val clf = Classifier(ctx, b)
                        try {
                            clf.scores(decode(m))
                        } finally {
                            clf.close()
                        }
                    }
                } catch (e: Exception) {
                }
                times.add(SystemClock.elapsedRealtime() - t0)
            }
            if (times.isNotEmpty()) {
                result[b] = times.average()
            }
        }
        return result
    }
}