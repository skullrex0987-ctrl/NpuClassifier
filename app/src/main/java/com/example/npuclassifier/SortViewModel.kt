package com.example.npuclassifier

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
    val history = MutableStateFlow<List<HistoryRow>>(emptyList())

    init {
        categories.value = CategoryStore.load(ctx)
        viewModelScope.launch {
            ScanController.state.collect { st ->
                running.value = st.running
                progress.value = st.done to st.total
                st.message?.let { message.value = it }
            }
        }
    }

    fun updateSettings(f: (SortSettings) -> SortSettings) {
        settings.update(f)
        prefs.save(settings.value)
        viewModelScope.launch(Dispatchers.IO) { redecideRows() }
    }

    fun setModel(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val cfg = settings.value.copy(model = id)
            settings.value = cfg
            prefs.save(cfg)
            Repo.resetForModelChange(ctx)
            categories.value = engine.ensureExamples(cfg, categories.value)
            CategoryStore.save(ctx, categories.value)
            redecideRows()
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
        viewModelScope.launch(Dispatchers.IO) { redecideRows() }
        message.value = "Settings reset"
    }

    fun clearResults() {
        viewModelScope.launch(Dispatchers.IO) {
            Repo.resetForModelChange(ctx)
            message.value = "Results cleared"
        }
    }

    fun clearExamples(category: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val cur = categories.value.toMutableList()
            val idx = cur.indexOfFirst { it.name == category }
            if (idx >= 0) {
                cur[idx] = cur[idx].copy(examples = emptyList())
                categories.value = cur
                CategoryStore.save(ctx, cur)
                redecideRows()
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
                val cur = categories.value.toMutableList()
                for (imp in imported) {
                    val idx = cur.indexOfFirst { it.name == imp.name }
                    if (idx >= 0) {
                        cur[idx] = imp
                    } else {
                        cur.add(imp)
                    }
                }
                categories.value = cur
                CategoryStore.save(ctx, cur)
                redecideRows()
                message.value = "Imported ${imported.size} categories"
            } catch (e: Exception) {
                message.value = "Import failed: ${e.message}"
            }
        }
    }

    fun runSpeedTest() {
        viewModelScope.launch(Dispatchers.IO) {
            val todo = Repo.page(ctx, Filter(), 0, 20).map { it.media }.ifEmpty { items.value.take(20).map { it.media } }
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

    fun loadUris(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val batchId = System.currentTimeMillis()
            val mediaItems = uris.map { Media.fromUri(ctx, it) }
            Repo.upsertPending(ctx, mediaItems, batchId)
            message.value = "${uris.size} files selected"
            ScanController.start(ctx, settings.value)
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
            val batchId = System.currentTimeMillis()
            Repo.upsertPending(ctx, found, batchId)
            message.value = "Found ${found.size} files"
            ScanController.start(ctx, settings.value)
        }
    }

    fun startScan() {
        if (running.value) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                categories.value = engine.ensureExamples(settings.value, categories.value)
                CategoryStore.save(ctx, categories.value)
                ScanController.start(ctx, settings.value)
            } catch (e: Exception) {
                message.value = "Error: ${e.message}"
            }
        }
    }

    fun stopScan() {
        ScanController.stop(ctx)
    }

    fun applyAuto() {
        viewModelScope.launch(Dispatchers.IO) {
            val cfg = settings.value
            val batchId = System.currentTimeMillis()
            var totalDone = 0
            var failed = 0
            var offset = 0
            while (true) {
                val page = Repo.page(ctx, Filter(statuses = setOf(Status.AUTO)), offset, 200)
                if (page.isEmpty()) break
                for (si in page) {
                    try {
                        val ops = FileOps.place(ctx, si.media, si.label, cfg, si.extraLabels)
                        Repo.addHistory(ctx, batchId, si, ops, "auto")
                        val destPath = ops.firstOrNull()?.dest
                        Repo.saveResults(ctx, listOf(si.copy(status = Status.DONE, destPath = destPath, note = "-> ${si.label}")))
                        totalDone++
                    } catch (e: Exception) {
                        failed++
                        Repo.saveResults(ctx, listOf(si.copy(status = Status.ERROR, note = e.message ?: "failed")))
                    }
                }
                offset += page.size
                if (page.size < 200) break
            }
            canUndo.value = true
            history.value = Repo.historyPage(ctx, 0, 100)
            message.value = "$totalDone ${if (cfg.move) "moved" else "copied"}, $failed failed"
        }
    }

    fun assign(si: SortItem, folder: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cfg = settings.value
                val ops = FileOps.place(ctx, si.media, folder, cfg, si.extraLabels)
                val batchId = System.currentTimeMillis()
                Repo.addHistory(ctx, batchId, si, ops, "review")
                val destPath = ops.firstOrNull()?.dest
                Repo.saveResults(ctx, listOf(si.copy(status = Status.DONE, destPath = destPath, note = "-> $folder")))
                learn(folder, si)
                history.value = Repo.historyPage(ctx, 0, 100)
                message.value = "Assigned to $folder"
            } catch (e: Exception) {
                message.value = "Failed: ${e.message}"
            }
        }
    }

    fun undo() {
        viewModelScope.launch(Dispatchers.IO) {
            val rows = Repo.historyPage(ctx, 0, 1)
            val batchId = rows.firstOrNull()?.batchId
            if (batchId != null) undoBatch(batchId) else {
                message.value = "Nothing to undo"
            }
        }
    }

    fun undoBatch(batchId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val batchRows = mutableListOf<HistoryRow>()
            var offset = 0
            while (true) {
                val p = Repo.historyPage(ctx, offset, 200)
                if (p.isEmpty()) break
                batchRows.addAll(p.filter { it.batchId == batchId })
                offset += p.size
                if (p.size < 200) break
            }
            var n = 0
            for (hr in batchRows) {
                try {
                    val moved = hr.srcPath != null && !File(hr.srcPath).exists()
                    FileOps.undo(ctx, Op(hr.srcPath, hr.destPath, moved))
                    val si = findMediaByDest(hr.destPath)
                    if (si != null) {
                        val newStatus = if (hr.action == "auto") Status.AUTO else Status.REVIEW
                        Repo.saveResults(ctx, listOf(si.copy(status = newStatus, destPath = null, note = "")))
                    }
                    Repo.markHistory(ctx, hr.id, "undone")
                    n++
                } catch (_: Exception) {
                }
            }
            canUndo.value = Repo.historyPage(ctx, 0, 1).isNotEmpty() && Repo.historyPage(ctx, 0, 1).any { it.status == "ok" }
            history.value = Repo.historyPage(ctx, 0, 100)
            message.value = "Undid $n files"
        }
    }

    fun undoOne(historyId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val hr = findHistory(historyId) ?: run {
                message.value = "History row not found"
                return@launch
            }
            try {
                val moved = hr.srcPath != null && !File(hr.srcPath).exists()
                FileOps.undo(ctx, Op(hr.srcPath, hr.destPath, moved))
                val si = findMediaByDest(hr.destPath)
                if (si != null) {
                    val newStatus = if (hr.action == "auto") Status.AUTO else Status.REVIEW
                    Repo.saveResults(ctx, listOf(si.copy(status = newStatus, destPath = null, note = "")))
                }
                Repo.markHistory(ctx, hr.id, "undone")
                history.value = Repo.historyPage(ctx, 0, 100)
                message.value = "Undid 1 file"
            } catch (e: Exception) {
                message.value = "Undo failed: ${e.message}"
            }
        }
    }

    fun changeCategory(historyId: Long, newLabel: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val hr = findHistory(historyId) ?: run {
                message.value = "History row not found"
                return@launch
            }
            try {
                val si = findMediaByDest(hr.destPath) ?: SortItem(
                    media = MediaItem(uri = Uri.fromFile(File(hr.destPath)), path = hr.destPath, name = File(hr.destPath).name, isVideo = false),
                    label = hr.label, status = Status.DONE, destPath = hr.destPath
                )
                val cfg = settings.value
                val ops = FileOps.place(ctx, si.media, newLabel, cfg, si.extraLabels)
                val newDest = ops.firstOrNull()?.dest ?: hr.destPath
                val updated = si.copy(label = newLabel, destPath = newDest, status = Status.DONE, note = "-> $newLabel")
                Repo.saveResults(ctx, listOf(updated))
                val batchId = System.currentTimeMillis()
                Repo.addHistory(ctx, batchId, updated, ops, "change")
                learn(newLabel, si)
                history.value = Repo.historyPage(ctx, 0, 100)
                message.value = "Category changed"
            } catch (e: Exception) {
                message.value = "Change failed: ${e.message}"
            }
        }
    }

    fun resolveMissing(ids: List<Long>, remove: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            for (id in ids) {
                Repo.markHistory(ctx, id, if (remove) "undone" else "ok")
            }
            history.value = Repo.historyPage(ctx, 0, 100)
            message.value = if (remove) "Removed missing entries" else "Kept missing entries"
        }
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { os -> Repo.exportHistoryCsv(ctx, os) }
                message.value = "Exported CSV"
            } catch (e: Exception) {
                message.value = "Export failed: ${e.message}"
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            Repo.clearHistory(ctx)
            history.value = emptyList()
            canUndo.value = false
            message.value = "History cleared"
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
                redecideRows()
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
            redecideRows()
            message.value = "Deleted \"$name\""
        }
    }

    fun suggest() {
        viewModelScope.launch(Dispatchers.Default) {
            suggestions.value = Suggest.clusters(Repo.page(ctx, Filter(), 0, 500))
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

                redecideRows()
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

    private fun redecideRows() {
        val cfg = settings.value
        val cats = categories.value
        viewModelScope.launch(Dispatchers.IO) {
            val candidates = if (cfg.model == "clip") engine.candidates(cfg, cats) else emptyList()
            var offset = 0
            val filter = Filter(statuses = setOf(Status.AUTO, Status.REVIEW))
            while (true) {
                val page = Repo.page(ctx, filter, offset, 500)
                if (page.isEmpty()) break
                val updated = page.map {
                    if (it.status == Status.AUTO || it.status == Status.REVIEW)
                        Decision.apply(it, cats, cfg, candidates) else it
                }
                Repo.saveResults(ctx, updated)
                offset += page.size
                if (page.size < 500) break
            }
        }
    }

    private fun redecide(cands: List<Candidate>? = null) {
        viewModelScope.launch(Dispatchers.IO) { redecideRows() }
    }

    private fun learn(folder: String, si: SortItem) {
        val emb = si.emb ?: return
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
        redecideRows()
    }

    private fun findMediaByDest(dest: String): SortItem? {
        var offset = 0
        while (true) {
            val p = Repo.page(ctx, Filter(statuses = setOf(Status.DONE)), offset, 500)
            if (p.isEmpty()) break
            val found = p.firstOrNull { it.destPath == dest }
            if (found != null) return found
            offset += p.size
            if (p.size < 500) break
        }
        return null
    }

    private fun findHistory(id: Long): HistoryRow? {
        var offset = 0
        while (true) {
            val p = Repo.historyPage(ctx, offset, 200)
            if (p.isEmpty()) break
            val found = p.firstOrNull { it.id == id }
            if (found != null) return found
            offset += p.size
            if (p.size < 200) break
        }
        return null
    }
}
