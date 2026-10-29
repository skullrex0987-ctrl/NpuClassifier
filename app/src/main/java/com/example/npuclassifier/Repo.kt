package com.example.npuclassifier

import android.content.Context

object Repo {
    fun upsertPending(ctx: Context, items: List<MediaItem>, batchId: Long): List<MediaItem> = items
    fun saveResults(ctx: Context, results: List<SortItem>) { }
    fun nextPending(ctx: Context, status: Status, limit: Int): List<MediaItem> = emptyList()
    fun counts(ctx: Context): Counts = Counts(0,0,0,0,0,0,0)
    fun page(ctx: Context, f: Filter, offset: Int, limit: Int): List<SortItem> = emptyList()
    fun get(ctx: Context, id: Long): SortItem? = null
    fun resetForModelChange(ctx: Context) { }
    fun addHistory(ctx: Context, batchId: Long, item: SortItem, ops: List<Op>, action: String) { }
    fun historyPage(ctx: Context, offset: Int, limit: Int): List<HistoryRow> = emptyList()
    fun markHistory(ctx: Context, id: Long, status: String) { }
    fun clearHistory(ctx: Context) { }
    fun exportHistoryCsv(ctx: Context, out: java.io.OutputStream) { }
    fun ftsSearch(ctx: Context, query: String, limit: Int): List<Long> = emptyList()
    fun embeddings(ctx: Context, model: String): List<Pair<Long, FloatArray>> = emptyList()
    fun saveEmbedding(ctx: Context, id: Long, model: String, emb: FloatArray) { }
    fun saveEnrichment(ctx: Context, id: Long, ocr: String, tags: List<String>) { }
    fun countMissingEmb(ctx: Context, model: String): Int = 0
}