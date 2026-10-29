package com.example.npuclassifier

import android.app.Application
import android.content.Context
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
    val speedText = MutableStateFlow("")

    init {
        categories.value = CategoryStore.load(ctx)
    }

    private var index = HashMap<Uri, Int>()
    private var job: Job? = null
    private val history = mutableListOf<Triple<Uri, Op, Status>>()

    fun updateSettings(f: (SortSettings) -> SortSettings) {
        settings.update(f)
        prefs.save(settings.value)
        redecide()
    }

    fun setModel(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val cfg = settings.value.copy(model = id)
            settings.value = cfg
            prefs.save(cfg)
            // clear results
            items.update { l -> l.map { it.copy(status = Status.PENDING, emb = null, frameItems = null, label = "", conf = 0f, gap = 0f, top = emptyList(), source = "", note = "") } }
            // ensure examples
            categories.value = engine.ensureExamples(cfg, categories.value)
            CategoryStore.save(ctx, categories.value)
            redecide()
            message.value = "Model changed to $id"
        }
    }

    fun applyPreset(name: String) {
        val (thr, gap, catThr, catGap) = when (name) {
            "Strict" -> listOf(0.70f, 0.15f, 0.85f, 0.05f)
            "Loose" -> listOf(0.35f, 0.03f, 0.72f, 0.01f)
            else -> listOf(0.50f, 0.08f, 0.80f, 0.03f)
        }
        updateSettings { it.copy(threshold = thr, minGap = gap, catThreshold = catThr, catGap = catGap) }
    }

    fun resetSettings() {
        val d = SortSettings()
        settings.value = d
        prefs.save(d)
        redecide()
        message.value = "Settings reset"
    }

    fun clearResults() {
        items.update { l -> l.map { SortItem(media = it.media) } }
        message.value = "Results cleared"
    }

    fun clearExamples(category: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val cur = categories.value.toMutableList()
            val idx = cur.indexOfFirst { it.name == category }
            if (idx >= 0) {
                cur[idx] = cur[idx].copy(examples = emptyList())
                categories.value = cur
                CategoryStore.save(ctx, cur)
                redecide()
                message.value = "Cleared examples for $category"
            }
        }
    }

    fun setPrompt(category: String, prompt: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val cur = categories.value.toMutableList()
            val idx = cur.indexOfFirst { it.name == category }
            if (idx >= 0) {
                cur[idx] = cur[idx].copy(prompt = prompt)
                categories.value = cur
                CategoryStore.save(ctx, cur)
            }
        }
    }

    fun exportCategories(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(CategoryStore.load(ctx).let { cats ->
                        // serialize
                        val arr = org.json.JSONArray()
                        for (cat in cats) {
                            val o = org.json.JSONObject()
                            o.put("name", cat.name)
                            o.put("prompt", cat.prompt)
                            val exArr = org.json.JSONArray()
                            for (ex in cat.examples) {
                                val e = org.json.JSONObject()
                                e.put("name", ex.name)
                                ex.path?.let { e.put("path", it) }
                                val embsObj = org.json.JSONObject()
                                for ((k, v) in ex.embs) {
                                    val buf = java.nio.ByteBuffer.allocate(v.size * 4).order(java.nio.ByteOrder.nativeOrder())
                                    buf.asFloatBuffer().put(v)
                                    embsObj.put(k, android.util.Base64.encodeToString(buf.array(), android.util.Base64.DEFAULT))
                                }
                                e.put("embs", embsObj)
                                exArr.put(e)
                            }
                            o.put("examples", exArr)
                            arr.put(o)
                        }
                        arr.toString(2).toByteArray()
                    })
                }
                message.value = "Categories exported"
            } catch (e: Exception) {
                message.value = "Export failed: ${e.message}"
            }
        }
    }

    fun importCategories(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return@launch
                val arr = org.json.JSONArray(text)
                val imported = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    val name = o.getString("name")
                    val prompt = o.optString("prompt", "")
                    val exArr = o.getJSONArray("examples")
                    val examples = (0 until exArr.length()).map { j ->
                        val e = exArr.getJSONObject(j)
                        val exName = e.getString("name")
                        val path = e.optString("path", null)
                        val embs = mutableMapOf<String, FloatArray>()
                        if (e.has("embs")) {
                            val embsObj = e.getJSONObject("embs")
                            val keys = embsObj.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                val embBytes = android.util.Base64.decode(embsObj.getString(k), android.util.Base64.DEFAULT)
                                val emb = FloatArray(embBytes.size / 4)
                                java.nio.ByteBuffer.wrap(embBytes).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer().get(emb)
                                embs[k] = emb
                            }
                        }
                        Example(exName, path, embs)
                    }
                    Category(name, examples, prompt)
                }
                // Merge by name
                val cur = categories.value.toMutableList()
                for (imp in imported) {
                    val idx = cur.indexOfFirst { it.name == imp.name }
                    if (idx >= 0) {
                        cur[idx] = imp // replace
                    } else {
                        cur.add(imp)
                    }
                }
                categories.value = cur
                CategoryStore.save(ctx, cur)
                redecide()
                message.value = "Imported ${imported.size} categories"
            } catch (e: Exception) {
                message.value = "Import failed: ${e.message}"
            }
        }
    }

    fun runSpeedTest() {
        viewModelScope.launch(Dispatchers.IO) {
            val todo = items.value.take(20).map { it.media }
            if (todo.isEmpty()) {
                speedText.value = "No files to test"
                return@launch
            }
            speedText.value = "Running..."
            val result = engine.speedTest(todo, settings.value)
            val lines = result.entries.map { "${it.key} ${it.value.toInt()} ms/file" }
            speedText.value = lines.joinToString("\n")
        }
    }

    private fun setItems(ms: List<MediaItem>) {
        index = HashMap<Uri, Int>().also { m -> ms.forEachIndexed { i, x -> m[x.uri] = i } }
        items.value = ms.map { SortItem(media = it) }
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
                // ensure examples have embeddings for current model
                categories.value = engine.ensureExamples(settings.value, categories.value)
                CategoryStore.save(ctx, categories.value)
                val cands = engine.candidates(settings.value, categories.value)
                engine.run(
                    todo, settings.value, categories.value,
                    onItem = { si ->
                        replace(si)
                        progress.value = done.incrementAndGet() to todo.size
                    },
                    onBackendError = { b, e -> message.value = "$b unavailable: $e" }
                )
                redecide(cands)
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
                    val op = FileOps.place(ctx, si.media, si.label, cfg).firstOrNull() ?: Op(null, "", false)
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
                val op = FileOps.place(ctx, si.media, folder, settings.value).firstOrNull() ?: Op(null, "", false)
                history.add(Triple(si.media.uri, op, Status.REVIEW))
                canUndo.value = true
                replace(si.copy(status = Status.DONE, note = "-> $folder"))
                val emb = si.emb
                if (emb != null) {
                    val model = settings.value.model
                    val cur = categories.value.toMutableList()
                    val idx = cur.indexOfFirst { it.name == folder }
                    if (idx >= 0) {
                        val ex = cur[idx].examples.find { it.name == si.media.name }
                        if (ex != null) {
                            val newEmbs = ex.embs.toMutableMap()
                            newEmbs[model] = emb
                            cur[idx] = cur[idx].copy(examples = cur[idx].examples.map {
                                if (it.name == si.media.name) it.copy(embs = newEmbs) else it
                            })
                        } else {
                            cur[idx] = cur[idx].copy(examples = cur[idx].examples + Example(si.media.name, si.media.path, mapOf(model to emb)))
                        }
                    } else {
                        cur.add(Category(folder, listOf(Example(si.media.name, si.media.path, mapOf(model to emb)))))
                    }
                    categories.value = cur
                    CategoryStore.save(ctx, cur)
                    val cands = engine.candidates(settings.value, cur)
                    redecide(cands)
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
                val model = settings.value.model
                val embs = engine.embedFiles(ms, settings.value)
                val examples = ms.zip(embs).mapNotNull { (m, e) ->
                    if (e == null) null else Example(m.name, m.path, mapOf(model to e))
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
                val cands = engine.candidates(settings.value, cur)
                redecide(cands)
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
            val cands = engine.candidates(settings.value, cur)
            redecide(cands)
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
                val model = settings.value.model
                val examples = members.mapNotNull { si ->
                    si.emb?.let { Example(si.media.name, si.media.path, mapOf(model to it)) }
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

                val cands = engine.candidates(settings.value, cur)
                redecide(cands)
                message.value = "Created \"$finalName\" from ${examples.size} examples"
            } catch (e: Exception) {
                message.value = "approveCluster failed: ${e.message}"
            }
        }
    }

    private fun removeSuggestion(cluster: Cluster) {
        val uris = cluster.members.map { it.media.uri }.toSet()
        suggestions.update { list -> list.filter { it !== cluster && it.members.map { m -> m.media.uri }.toSet() != uris } }
    }

    fun dismissCluster(cluster: Cluster) {
        removeSuggestion(cluster)
    }

    private fun redecide(cands: List<Candidate>? = null) {
        val cfg = settings.value
        val cats = categories.value
        viewModelScope.launch(Dispatchers.IO) {
            val candidates = cands ?: if (cfg.model == "clip") engine.candidates(cfg, cats) else emptyList()
            items.update { l ->
                l.map {
                    if (it.status == Status.AUTO || it.status == Status.REVIEW)
                        Decision.apply(it, cats, cfg, candidates) else it
                }
            }
        }
    }
}