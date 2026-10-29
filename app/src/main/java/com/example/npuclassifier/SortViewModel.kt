package com.example.npuclassifier

import android.app.Application
import android.graphics.ImageDecoder
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

    init {
        categories.value = CategoryStore.load(ctx)
    }

    private var index = HashMap<Uri, Int>()
    private var job: Job? = null
    private val history = mutableListOf<Triple<Uri, Op, Status>>()

    fun updateSettings(f: (SortSettings) -> SortSettings) {
        settings.update(f)
        prefs.save(settings.value)
        // Threshold / gap / category changes re-decide existing results instantly (no re-scan needed).
        redecide()
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
                // The engine decides with built-in labels; categories (and clustering suggestions)
                // are applied here so the result reflects what the user has taught the app.
                redecide()
                suggest()
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
                // Learn from the assignment: add this file's embedding as an example.
                val emb = si.emb
                if (emb != null) {
                    val cur = categories.value.toMutableList()
                    val idx = cur.indexOfFirst { it.name == folder }
                    if (idx >= 0) {
                        cur[idx] = cur[idx].copy(examples = cur[idx].examples + Example(si.media.name, si.media.path, emb))
                    } else {
                        cur.add(Category(folder, listOf(Example(si.media.name, si.media.path, emb))))
                    }
                    categories.value = cur
                    CategoryStore.save(ctx, cur)
                    redecide()
                }
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
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ms = uris.map { Media.fromUri(ctx, it) }
                val embs = if (settings.value.model == "clip") {
                    val clip = ClipClassifier(ctx)
                    try {
                        ms.map { m ->
                            try {
                                val bmp = if (m.isVideo) null else {
                                    // decode
                                    val src = if (m.path != null) ImageDecoder.createSource(File(m.path))
                                    else ImageDecoder.createSource(ctx.contentResolver, m.uri)
                                    ImageDecoder.decodeBitmap(src)
                                }
                                bmp?.let { clip.imageEmbed(it) }
                            } catch (e: Exception) { null }
                        }
                    } finally {
                        clip.close()
                    }
                } else {
                    engine.embedFiles(ms)
                }
                val examples = ms.zip(embs).mapNotNull { (m, e) ->
                    if (e == null) null else Example(m.name, m.path, e)
                }
                if (examples.isEmpty()) {
                    message.value = "Could not embed any of ${uris.size} files"
                    return@launch
                }
                val cur = categories.value.toMutableList()
                val idx = cur.indexOfFirst { it.name == name }
                if (idx >= 0) {
                    cur[idx] = cur[idx].copy(examples = cur[idx].examples + examples)
                } else {
                    cur.add(Category(name, examples))
                }
                categories.value = cur
                CategoryStore.save(ctx, cur)
                redecide()
                message.value = "Category \"$name\" now has ${examples.size} example(s)"
            } catch (e: Exception) {
                message.value = "createCategory failed: ${e.message}"
            }
        }
    }

    fun deleteCategory(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val cur = categories.value.toMutableList()
            val removed = cur.removeAll { it.name == name }
            if (!removed) return@launch
            categories.value = cur
            CategoryStore.save(ctx, cur)
            redecide()
            message.value = "Deleted \"$name\""
        }
    }

    fun suggest() {
        viewModelScope.launch(Dispatchers.Default) {
            suggestions.value = Suggest.clusters(items.value)
        }
    }

    fun approveCluster(cluster: Cluster, finalName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val members = cluster.members.take(20)
                val examples = members.mapNotNull { si ->
                    si.emb?.let { Example(si.media.name, si.media.path, it) }
                }
                if (examples.isEmpty()) {
                    message.value = "Cluster has no embeddings"
                    return@launch
                }
                val cur = categories.value.toMutableList()
                val idx = cur.indexOfFirst { it.name == finalName }
                if (idx >= 0) cur[idx] = cur[idx].copy(examples = cur[idx].examples + examples)
                else cur.add(Category(finalName, examples))
                categories.value = cur
                CategoryStore.save(ctx, cur)

                removeSuggestion(cluster)

                redecide()
                message.value = "Created \"$finalName\" from ${examples.size} examples"
            } catch (e: Exception) {
                message.value = "approveCluster failed: ${e.message}"
            }
        }
    }

    /** Removes a suggestion by identity, falling back to "same members" if the UI handed back a copy. */
    private fun removeSuggestion(cluster: Cluster) {
        val uris = cluster.members.map { it.media.uri }.toSet()
        suggestions.update { list -> list.filter { it !== cluster && it.members.map { m -> m.media.uri }.toSet() != uris } }
    }

    fun dismissCluster(cluster: Cluster) {
        removeSuggestion(cluster)
    }

    private fun redecide() {
        val cfg = settings.value
        val cats = categories.value
        items.update { l ->
            l.map {
                if (it.status == Status.AUTO || it.status == Status.REVIEW)
                    Decision.apply(it, cats, cfg) else it
            }
        }
    }
}
