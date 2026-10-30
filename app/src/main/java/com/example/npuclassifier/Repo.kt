package com.example.npuclassifier

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream

object Repo {

    private fun db(ctx: Context): SQLiteDatabase = Db.get(ctx)

    private fun keyOf(m: MediaItem): String = m.path ?: m.uri.toString()

    private fun floatArrayToBlob(arr: FloatArray): ByteArray {
        val bb = java.nio.ByteBuffer.allocate(arr.size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (v in arr) bb.putFloat(v)
        return bb.array()
    }

    private fun blobToFloatArray(blob: ByteArray): FloatArray {
        val bb = java.nio.ByteBuffer.wrap(blob).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(blob.size / 4)
        for (i in out.indices) out[i] = bb.getFloat()
        return out
    }

    private fun cursorToMediaItem(c: Cursor): MediaItem {
        val id = c.getLong(c.getColumnIndexOrThrow("id"))
        val uri = c.getString(c.getColumnIndexOrThrow("uri"))
        val path = c.getString(c.getColumnIndexOrThrow("path"))
        val name = c.getString(c.getColumnIndexOrThrow("name"))
        val isVideo = c.getInt(c.getColumnIndexOrThrow("is_video")) == 1
        return MediaItem(id = id, uri = android.net.Uri.parse(uri), path = path, name = name, isVideo = isVideo)
    }

    private fun cursorToSortItem(c: Cursor): SortItem {
        val id = c.getLong(c.getColumnIndexOrThrow("id"))
        val uri = c.getString(c.getColumnIndexOrThrow("uri"))
        val path = c.getString(c.getColumnIndexOrThrow("path"))
        val name = c.getString(c.getColumnIndexOrThrow("name"))
        val isVideo = c.getInt(c.getColumnIndexOrThrow("is_video")) == 1
        val media = MediaItem(id = id, uri = android.net.Uri.parse(uri), path = path, name = name, isVideo = isVideo)
        val statusStr = c.getString(c.getColumnIndexOrThrow("status")) ?: "PENDING"
        val status = runCatching { Status.valueOf(statusStr) }.getOrDefault(Status.PENDING)
        val label = c.getString(c.getColumnIndexOrThrow("label")) ?: ""
        val conf = c.getFloat(c.getColumnIndexOrThrow("conf"))
        val gap = c.getFloat(c.getColumnIndexOrThrow("gap"))
        val source = c.getString(c.getColumnIndexOrThrow("source")) ?: ""
        val note = c.getString(c.getColumnIndexOrThrow("note")) ?: ""
        val ocr = c.getString(c.getColumnIndexOrThrow("ocr")) ?: ""
        val tagsStr = c.getString(c.getColumnIndexOrThrow("tags")) ?: ""
        val tags = if (tagsStr.isBlank()) emptyList() else tagsStr.split(",")
        val thumb = c.getString(c.getColumnIndexOrThrow("thumb"))
        val destPath = c.getString(c.getColumnIndexOrThrow("dest_path"))
        val topJson = c.getString(c.getColumnIndexOrThrow("top_json"))
        val top = parseTop(topJson)
        val extraLabelsStr = c.getString(c.getColumnIndexOrThrow("extra_labels")) ?: ""
        val extraLabels = if (extraLabelsStr.isBlank()) emptyList() else extraLabelsStr.split(",")
        val embBlob = c.getBlobSafe("emb_clip") ?: c.getBlobSafe("emb_mnet") ?: c.getBlobSafe("emb_gemma")
        val emb = embBlob?.let { blobToFloatArray(it) }
        return SortItem(
            id = id, media = media, label = label, conf = conf, gap = gap, top = top,
            status = status, note = note, emb = emb,
            source = source, extraLabels = extraLabels, tags = tags, ocr = ocr,
            thumb = thumb, destPath = destPath
        )
    }

    private fun Cursor.getBlobSafe(col: String): ByteArray? {
        val idx = getColumnIndex(col)
        if (idx < 0) return null
        return if (isNull(idx)) null else getBlob(idx)
    }

    private fun parseTop(json: String?): List<Pair<String, Float>> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                o.getString("name") to o.getDouble("p").toFloat()
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun topToJson(top: List<Pair<String, Float>>): String {
        val arr = JSONArray()
        for ((n, p) in top) {
            val o = JSONObject()
            o.put("name", n)
            o.put("p", p.toDouble())
            arr.put(o)
        }
        return arr.toString()
    }

    fun upsertPending(ctx: Context, items: List<MediaItem>, batchId: Long): List<MediaItem> {
        val d = db(ctx)
        val result = mutableListOf<MediaItem>()
        d.beginTransaction()
        try {
            for (m in items) {
                val key = keyOf(m)
                val cur = d.rawQuery("SELECT id FROM media WHERE key=?", arrayOf(key))
                val exists = cur.use { it.moveToFirst() }
                if (exists) {
                    val id = cur.getLong(0)
                    result.add(m.copy(id = id))
                } else {
                    val cv = android.content.ContentValues()
                    cv.put("key", key)
                    cv.put("path", m.path)
                    cv.put("uri", m.uri.toString())
                    cv.put("name", m.name)
                    cv.put("is_video", if (m.isVideo) 1 else 0)
                    cv.put("status", "PENDING")
                    cv.put("batch_id", batchId)
                    cv.put("scanned_at", System.currentTimeMillis())
                    val id = d.insert("media", null, cv)
                    // sync fts
                    d.execSQL("INSERT INTO media_fts(rowid, name, ocr, tags, label) VALUES (?, ?, ?, ?, ?)", arrayOf(id, m.name, null, null, null))
                    result.add(m.copy(id = id))
                }
            }
            d.setTransactionSuccessful()
        } finally {
            d.endTransaction()
        }
        return result
    }

    fun saveEnrichment(ctx: Context, mediaId: Long, ocr: String, tags: List<String>) {
        val d = db(ctx)
        val cv = android.content.ContentValues()
        cv.put("ocr", ocr.take(4000))
        cv.put("tags", tags.joinToString(","))
        d.update("media", cv, "id=?", arrayOf(mediaId.toString()))
        // sync fts
        d.execSQL("INSERT OR REPLACE INTO media_fts(rowid, name, ocr, tags, label) SELECT id, name, ?, ?, label FROM media WHERE id=?", arrayOf(ocr.take(4000), tags.joinToString(","), mediaId))
    }

    fun saveResults(ctx: Context, results: List<SortItem>) {
        val d = db(ctx)
        d.beginTransaction()
        try {
            for (si in results) {
                val cv = android.content.ContentValues()
                cv.put("status", si.status.name)
                cv.put("label", si.label)
                cv.put("conf", si.conf)
                cv.put("gap", si.gap)
                cv.put("top_json", topToJson(si.top))
                cv.put("source", si.source)
                cv.put("note", si.note)
                cv.put("ocr", si.ocr)
                cv.put("tags", si.tags.joinToString(","))
                cv.put("thumb", si.thumb)
                cv.put("dest_path", si.destPath)
                cv.put("extra_labels", si.extraLabels.joinToString(","))
                cv.put("backend", si.backend?.name)
                cv.put("ms", si.ms)
                if (si.id > 0) {
                    d.update("media", cv, "id=?", arrayOf(si.id.toString()))
                } else {
                    d.update("media", cv, "key=?", arrayOf(keyOf(si.media)))
                }
                val id = if (si.id > 0) si.id else 0L
                d.execSQL("DELETE FROM media_fts WHERE docid=?", arrayOf(id))
                try {
                    d.execSQL("INSERT INTO media_fts(docid, name, ocr, tags, label) VALUES (?,?,?,?,?)",
                        arrayOf(id, si.media.name, si.ocr, si.tags.joinToString(" "), si.label))
                } catch (e: Exception) { }
            }
            d.setTransactionSuccessful()
        } finally {
            d.endTransaction()
        }
    }

    fun claim(ctx: Context, status: Status, limit: Int): List<MediaItem> {
        val d = db(ctx)
        val targetRunning = if (status == Status.PENDING) "RUNNING_FAST" else "RUNNING_SMART"
        val out = mutableListOf<MediaItem>()
        d.beginTransaction()
        try {
            val cur = d.rawQuery("SELECT * FROM media WHERE status=? LIMIT ?", arrayOf(status.name, limit.toString()))
            val ids = mutableListOf<Long>()
            cur.use { while (it.moveToNext()) { ids.add(it.getLong(it.getColumnIndexOrThrow("id"))) ; out.add(cursorToMediaItem(it)) } }
            for (id in ids) {
                val cv = android.content.ContentValues()
                cv.put("status", targetRunning)
                d.update("media", cv, "id=?", arrayOf(id.toString()))
            }
            d.setTransactionSuccessful()
        } finally {
            d.endTransaction()
        }
        // update the returned items' status is not needed (MediaItem has no status)
        return out
    }

    fun releaseRunning(ctx: Context) {
        val d = db(ctx)
        d.beginTransaction()
        try {
            d.execSQL("UPDATE media SET status='PENDING' WHERE status='RUNNING_FAST'")
            d.execSQL("UPDATE media SET status='NEEDS_SMART' WHERE status='RUNNING_SMART'")
            d.setTransactionSuccessful()
        } finally {
            d.endTransaction()
        }
    }
    fun nextPending(ctx: Context, status: Status, limit: Int): List<MediaItem> {
        val d = db(ctx)
        val cur = d.rawQuery("SELECT * FROM media WHERE status=? LIMIT ?", arrayOf(status.name, limit.toString()))
        val out = mutableListOf<MediaItem>()
        cur.use { while (it.moveToNext()) out.add(cursorToMediaItem(it)) }
        return out
    }

    fun counts(ctx: Context): Counts {
        val d = db(ctx)
        val cur = d.rawQuery("SELECT status, COUNT(*) FROM media GROUP BY status", null)
        var total = 0
        var pending = 0; var needsSmart = 0; var running = 0; var auto = 0; var review = 0; var done = 0; var error = 0
        cur.use {
            while (it.moveToNext()) {
                val s = it.getString(0)
                val n = it.getInt(1)
                total += n
                when (s) {
                    "PENDING" -> pending = n
                    "NEEDS_SMART" -> needsSmart = n
                    "RUNNING_FAST", "RUNNING_SMART" -> running += n
                    "AUTO" -> auto = n
                    "REVIEW" -> review = n
                    "DONE" -> done = n
                    "ERROR" -> error = n
                }
            }
        }
        return Counts(total, pending, needsSmart, running, auto, review, done, error)
    }

    fun page(ctx: Context, f: Filter, offset: Int, limit: Int): List<SortItem> {
        val d = db(ctx)
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (f.statuses.isNotEmpty()) {
            where.add("status IN (${f.statuses.joinToString(",") { "?" }})")
            f.statuses.forEach { args.add(it.name) }
        }
        if (f.processedOnly) {
            where.add("status IN ('AUTO','REVIEW','DONE','ERROR')")
        }
        if (f.label != null) {
            where.add("label=?")
            args.add(f.label)
        }
        val order = if (f.latestFirst) "id DESC" else "id ASC"
        val sql = "SELECT * FROM media" + (if (where.isEmpty()) "" else " WHERE " + where.joinToString(" AND ")) + " ORDER BY $order LIMIT ? OFFSET ?"
        args.add(limit.toString())
        args.add(offset.toString())
        val cur = d.rawQuery(sql, args.toTypedArray())
        val out = mutableListOf<SortItem>()
        cur.use { while (it.moveToNext()) out.add(cursorToSortItem(it)) }
        return out
    }

    fun get(ctx: Context, id: Long): SortItem? {
        val d = db(ctx)
        val cur = d.rawQuery("SELECT * FROM media WHERE id=?", arrayOf(id.toString()))
        return cur.use { if (it.moveToFirst()) cursorToSortItem(it) else null }
    }

    fun resetForModelChange(ctx: Context) {
        val d = db(ctx)
        d.execSQL("UPDATE media SET status='PENDING', label='', conf=0, gap=0, top_json=NULL, source='', note='' WHERE status IN ('AUTO','REVIEW','DONE')")
    }

    fun addHistory(ctx: Context, batchId: Long, item: SortItem, ops: List<Op>, action: String) {
        val d = db(ctx)
        for (op in ops) {
            val cv = android.content.ContentValues()
            cv.put("batch_id", batchId)
            cv.put("ts", System.currentTimeMillis())
            cv.put("media_id", item.id)
            cv.put("src_path", op.src)
            cv.put("dest_path", op.dest)
            cv.put("action", action)
            cv.put("label", item.label)
            cv.put("conf", item.conf)
            cv.put("source", item.source)
            cv.put("thumb", item.thumb)
            cv.put("ocr_snippet", item.ocr.take(200))
            cv.put("tags", item.tags.joinToString(","))
            cv.put("status", "ok")
            d.insert("history", null, cv)
        }
    }

    fun historyPage(ctx: Context, offset: Int, limit: Int): List<HistoryRow> {
        val d = db(ctx)
        val cur = d.rawQuery("SELECT * FROM history ORDER BY id DESC LIMIT ? OFFSET ?", arrayOf(limit.toString(), offset.toString()))
        val out = mutableListOf<HistoryRow>()
        cur.use {
            while (it.moveToNext()) {
                out.add(HistoryRow(
                    id = it.getLong(it.getColumnIndexOrThrow("id")),
                    batchId = it.getLong(it.getColumnIndexOrThrow("batch_id")),
                    ts = it.getLong(it.getColumnIndexOrThrow("ts")),
                    srcPath = it.getString(it.getColumnIndexOrThrow("src_path")),
                    destPath = it.getString(it.getColumnIndexOrThrow("dest_path")),
                    action = it.getString(it.getColumnIndexOrThrow("action")),
                    label = it.getString(it.getColumnIndexOrThrow("label")),
                    conf = it.getFloat(it.getColumnIndexOrThrow("conf")),
                    source = it.getString(it.getColumnIndexOrThrow("source")),
                    thumb = it.getString(it.getColumnIndexOrThrow("thumb")),
                    ocrSnippet = it.getString(it.getColumnIndexOrThrow("ocr_snippet")) ?: "",
                    tags = it.getString(it.getColumnIndexOrThrow("tags")) ?: "",
                    status = it.getString(it.getColumnIndexOrThrow("status"))
                ))
            }
        }
        return out
    }

    fun markHistory(ctx: Context, id: Long, status: String) {
        val d = db(ctx)
        val cv = android.content.ContentValues()
        cv.put("status", status)
        d.update("history", cv, "id=?", arrayOf(id.toString()))
    }

    fun clearHistory(ctx: Context) {
        val d = db(ctx)
        d.execSQL("DELETE FROM history")
    }

    fun exportHistoryCsv(ctx: Context, out: OutputStream) {
        val w = out.bufferedWriter()
        w.write("id,batch_id,ts,src_path,dest_path,action,label,conf,source,thumb,ocr_snippet,tags,status\n")
        var offset = 0
        while (true) {
            val page = historyPage(ctx, offset, 500)
            if (page.isEmpty()) break
            for (hr in page) {
                w.write(csvRow(hr))
            }
            offset += page.size
            if (page.size < 500) break
        }
        w.flush()
    }

    private fun csvRow(hr: HistoryRow): String {
        val fields = listOf(
            hr.id.toString(), hr.batchId.toString(), hr.ts.toString(),
            csvEscape(hr.srcPath ?: ""), csvEscape(hr.destPath), hr.action,
            csvEscape(hr.label), hr.conf.toString(), hr.source,
            csvEscape(hr.thumb ?: ""), csvEscape(hr.ocrSnippet), csvEscape(hr.tags), hr.status
        )
        return fields.joinToString(",") + "\n"
    }

    private fun csvEscape(s: String): String {
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\""
        }
        return s
    }

    fun ftsSearch(ctx: Context, query: String, limit: Int): List<Long> {
        val d = db(ctx)
        val tokens = query.trim().split(Regex("\\s+"))
            .map { it.replace(Regex("[^A-Za-z0-9]"), "") }
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        val q = tokens.joinToString(" ") { "$it*" }
        return try {
            val cur = d.rawQuery("SELECT docid FROM media_fts WHERE media_fts MATCH ? ORDER BY rank LIMIT ?", arrayOf(q, limit.toString()))
            val out = mutableListOf<Long>()
            cur.use { while (it.moveToNext()) out.add(it.getLong(0)) }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun embeddings(ctx: Context, model: String): List<Pair<Long, FloatArray>> {
        val col = when (model) {
            "mnet" -> "emb_mnet"
            "clip" -> "emb_clip"
            "gemma" -> "emb_gemma"
            else -> "emb_mnet"
        }
        val d = db(ctx)
        val cur = d.rawQuery("SELECT id, $col FROM media WHERE $col IS NOT NULL", null)
        val out = mutableListOf<Pair<Long, FloatArray>>()
        cur.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                val blob = it.getBlob(1)
                out.add(id to blobToFloatArray(blob))
            }
        }
        return out
    }

    fun saveEmbedding(ctx: Context, id: Long, model: String, emb: FloatArray) {
        val col = when (model) {
            "mnet" -> "emb_mnet"
            "clip" -> "emb_clip"
            "gemma" -> "emb_gemma"
            else -> "emb_mnet"
        }
        val d = db(ctx)
        val cv = android.content.ContentValues()
        cv.put(col, floatArrayToBlob(emb))
        d.update("media", cv, "id=?", arrayOf(id.toString()))
    }

    fun countMissingEmb(ctx: Context, model: String): Int {
        val col = when (model) {
            "mnet" -> "emb_mnet"
            "clip" -> "emb_clip"
            "gemma" -> "emb_gemma"
            else -> "emb_mnet"
        }
        val d = db(ctx)
        val cur = d.rawQuery("SELECT COUNT(*) FROM media WHERE $col IS NULL", null)
        return cur.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }
}