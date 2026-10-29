package com.example.npuclassifier

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class SortViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    private val prefs = Prefs(ctx)
    private val engine = SortEngine(ctx)

    val settings = MutableStateFlow(prefs.load())
    val items = MutableStateFlow<List<SortItem>>(emptyList())
    val progress = MutableStateFlow(0 to 0)
    val running = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val canUndo = MutableStateFlow(false)
    val categories = MutableStateFlow<List<Category>>(emptyList())
    val suggestions = MutableStateFlow<List<Cluster>>(emptyList())

    private var index = HashMap<Uri, Int>()
    private var job: Job? = null
    private val history = mutableListOf<Triple<Uri, Op, Status>>()

    fun updateSettings(f: (SortSettings) -> SortSettings) {
        settings.update(f)
        prefs.save(settings.value)
        // Threshold / gap changes re-decide existing results instantly (no re-scan needed).
        val cfg = settings.value
        items.update { l ->
            l.map {
                if (it.status == Status.AUTO || it.status == Status.REVIEW)
                    it.copy(status = SortEngine.decide(it.conf, it.gap, cfg)) else it
            }
        }
    }

    private fun setItems(ms: List<MediaItem>) {
        index = HashMap<Uri, Int>().also { m -> ms.forEachIndexed { i, x -> m[x.uri] = i } }
        items.value = ms.map { SortItem(it) }
        history.clear()
        canUndo.value = false
        progress.value = 0 to ms.size
    }

    fun loadUris(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            setItems(uris.map { Media.fromUri(ctx, it) })
            message.value = "${uris.size} files selected"
        }
    }

    fun loadFolder(tree: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val path = Media.treePath(tree)
            if (path == null || !File(path).isDirectory) {
                message.value = "Could not read that folder"
                return@launch
            }
            val cfg = settings.value
            val ex = cfg.excludes.split(",").map { it.trim() }.filter { it.isNotEmpty() } + cfg.destRoot
            val found = Media.scanFolder(File(path), ex)
            setItems(found)
            message.value = "Found ${found.size} files"
        }
    }

    private fun replace(si: SortItem) {
        val i = index[si.media.uri] ?: return
        items.update { l -> if (i < l.size) l.toMutableList().also { it[i] = si } else l }
    }

    fun startScan() {
        if (running.value) return
        val todo = items.value.filter { it.status == Status.PENDING || it.status == Status.ERROR }.map { it.media }
        if (todo.isEmpty()) {
            message.value = "Nothing to scan"
            return
        }
        running.value = true
        val done = AtomicInteger(0)
        progress.value = 0 to todo.size
        job = viewModelScope.launch(Dispatchers.Default) {
            try {
                engine.run(
                    todo, settings.value, categories.value,
                    onItem = { si ->
                        replace(si)
                        progress.value = done.incrementAndGet() to todo.size
                    },
                    onBackendError = { b, e -> message.value = "$b unavailable: $e" }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                message.value = "Error: ${e.message}"
            } finally {
                running.value = false
            }
        }
    }

    fun stopScan() {
        job?.cancel()
    }

    fun applyAuto() {
        viewModelScope.launch(Dispatchers.IO) {
            val cfg = settings.value
            history.clear()
            var failed = 0
            for (si in items.value.filter { it.status == Status.AUTO }) {
                try {
                    val op = FileOps.place(ctx, si.media, si.label, cfg)
                    history.add(Triple(si.media.uri, op, Status.AUTO))
                    replace(si.copy(status = Status.DONE, note = "-> ${si.label}"))
                } catch (e: Exception) {
                    failed++
                    replace(si.copy(status = Status.ERROR, note = e.message ?: "failed"))
                }
            }
            canUndo.value = history.isNotEmpty()
            message.value = "${history.size} ${if (cfg.move) "moved" else "copied"}, $failed failed"
        }
    }

    fun assign(si: SortItem, folder: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val op = FileOps.place(ctx, si.media, folder, settings.value)
                history.add(Triple(si.media.uri, op, Status.REVIEW))
                canUndo.value = true
                replace(si.copy(status = Status.DONE, note = "-> $folder"))
            } catch (e: Exception) {
                message.value = "Failed: ${e.message}"
            }
        }
    }

    fun undo() {
        viewModelScope.launch(Dispatchers.IO) {
            var n = 0
            for ((uri, op, prev) in history.reversed()) {
                try {
                    FileOps.undo(ctx, op)
                    val i = index[uri]
                    if (i != null) items.value.getOrNull(i)?.let { replace(it.copy(status = prev, note = "")) }
                    n++
                } catch (_: Exception) {
                }
            }
            history.clear()
            canUndo.value = false
            message.value = "Undid $n files"
        }
    }

    fun createCategory(name: String, uris: List<Uri>) {
        // Stub - Agent C will implement
    }

    fun deleteCategory(name: String) {
        // Stub - Agent C will implement
    }

    fun suggest() {
        // Stub - Agent C will implement
    }

    fun approveCluster(cluster: Cluster, finalName: String) {
        // Stub - Agent C will implement
    }

    fun dismissCluster(cluster: Cluster) {
        // Stub - Agent C will implement
    }

    private fun redecide() {
        // Stub - Agent C will implement
    }
}
