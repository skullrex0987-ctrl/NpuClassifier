package com.example.npuclassifier

import android.content.Context
import kotlin.math.sqrt

object SearchEngine {
    fun indexStatus(ctx: Context, cfg: SortSettings): Pair<Int, Int> {
        val total = Repo.counts(ctx).total
        val emb = Repo.embeddings(ctx, cfg.searchModel)
        return Pair(emb.size, total)
    }

    fun search(ctx: Context, query: String, cfg: SortSettings, f: Filter): List<SortItem> {
        val ftsIds = Repo.ftsSearch(ctx, query, 50)
        val semanticIds = try {
            val embedder = ClipEmbedder(ctx, Backend.CPU)
            val qEmb = embedder.embedText(query) ?: FloatArray(0)
            if (qEmb.isEmpty()) emptyList() else {
                val all = Repo.embeddings(ctx, cfg.searchModel)
                all.map { (id, emb) -> id to cosine(qEmb, emb) }
                    .sortedByDescending { it.second }
                    .take(50)
                    .map { it.first }
            }
        } catch (e: Exception) {
            emptyList()
        }
        val merged = SearchUtil.rrfMerge(ftsIds, semanticIds)
        return merged.mapNotNull { id -> Repo.get(ctx, id) }
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f; var na = 0f; var nb = 0f
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        val denom = sqrt(na.toDouble()) * sqrt(nb.toDouble())
        return if (denom == 0.0) 0f else (dot / denom).toFloat()
    }
}