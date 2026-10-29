package com.example.npuclassifier

import kotlin.math.exp

object Decision {
    // Cache of CLIP text embeddings: label -> embedding
    private val clipTextCache = mutableMapOf<String, FloatArray>()

    fun cacheClipText(label: String, emb: FloatArray) {
        clipTextCache[label] = emb
    }

    fun hasClipText(label: String): Boolean = label in clipTextCache

    fun clearClipCache() {
        clipTextCache.clear()
    }

    fun matchCategories(emb: FloatArray, cats: List<Category>): List<Pair<String, Float>> {
        if (cats.isEmpty()) return emptyList()
        val results = mutableListOf<Pair<String, Float>>()
        for (cat in cats) {
            val sims = cat.examples.filter { it.emb.size == emb.size }.map { Emb.cosine(emb, it.emb) }
            val score = if (sims.isEmpty()) 0f else sims.sortedDescending().take(3).average().toFloat()
            results.add(cat.name to score)
        }
        return results.sortedByDescending { it.second }
    }

    fun apply(item: SortItem, cats: List<Category>, cfg: SortSettings): SortItem {
        if (cfg.model == "clip") {
            return applyClip(item, cats, cfg)
        }
        // MobileNet (Phase 2 behavior)
        val emb = item.emb
        if (cfg.useMine && emb != null && cats.isNotEmpty()) {
            val matches = matchCategories(emb, cats)
            if (matches.isNotEmpty()) {
                val best = matches[0]
                val second = matches.getOrElse(1) { "" to 0f }
                if (best.second >= cfg.catThreshold && (best.second - second.second) >= cfg.catGap) {
                    return item.copy(
                        label = best.first,
                        status = Status.AUTO,
                        source = "mine"
                    )
                }
            }
        }
        if (cfg.useBuiltin && item.top.isNotEmpty()) {
            val conf = item.conf
            val gap = item.gap
            if (conf >= cfg.threshold && gap >= cfg.minGap) {
                return item.copy(
                    label = SortEngine.prettyLabel(item.top[0].first),
                    status = Status.AUTO,
                    source = "builtin"
                )
            }
        }
        return item.copy(status = Status.REVIEW, source = "")
    }

    private fun applyClip(item: SortItem, cats: List<Category>, cfg: SortSettings): SortItem {
        val emb = item.emb ?: return item.copy(status = Status.REVIEW, source = "")

        // a) Use my categories (taught examples)
        if (cfg.useMine) {
            val catsWithExamples = cats.filter { it.examples.isNotEmpty() }
            if (catsWithExamples.isNotEmpty()) {
                val matches = matchCategories(emb, catsWithExamples)
                if (matches.isNotEmpty()) {
                    val best = matches[0]
                    val second = matches.getOrElse(1) { "" to 0f }
                    if (best.second >= cfg.catThreshold && (best.second - second.second) >= cfg.catGap) {
                        return item.copy(
                            label = best.first,
                            status = Status.AUTO,
                            source = "mine"
                        )
                    }
                }
            }
        }

        // b) TEXT RULE: zero-shot
        val candidates = mutableListOf<Pair<String, String>>() // (name, source)
        if (cfg.useMine) {
            for (cat in cats) {
                candidates.add(cat.name to "mine")
            }
        }
        if (cfg.useBuiltin) {
            for (label in BuiltinLabels.labels) {
                candidates.add(label to "builtin")
            }
        }

        if (candidates.isEmpty()) {
            return item.copy(status = Status.REVIEW, source = "")
        }

        // Compute similarities
        val sims = mutableListOf<Pair<String, Pair<String, Float>>>() // (name, source, sim)
        for ((name, source) in candidates) {
            val textEmb = clipTextCache[name] ?: continue
            val sim = Emb.cosine(emb, textEmb)
            sims.add(name to (source to sim))
        }

        if (sims.isEmpty()) {
            return item.copy(status = Status.REVIEW, source = "")
        }

        // Softmax over 100 * cosine
        val scaled = sims.map { 100f * it.second.second }
        val maxScaled = scaled.maxOrNull() ?: 0f
        val exps = scaled.map { exp((it - maxScaled).toDouble()).toFloat() }
        val sumExps = exps.sum()
        val probs = exps.map { it / sumExps }

        // Build candidate list with probs
        val candidateList = sims.mapIndexed { i, (name, srcSim) ->
            Triple(name, srcSim.first, probs[i])
        }.sortedByDescending { it.third }

        // Top 3 for item.top
        val top3 = candidateList.take(3).map { it.first to it.third }

        val best = candidateList[0]
        val second = candidateList.getOrElse(1) { Triple("", "builtin", 0f) }
        val bestP = best.third
        val secondP = second.third

        if (bestP >= cfg.threshold && (bestP - secondP) >= cfg.minGap) {
            return item.copy(
                label = best.first,
                conf = bestP,
                gap = bestP - secondP,
                top = top3,
                status = Status.AUTO,
                source = best.second
            )
        }

        return item.copy(
            conf = bestP,
            gap = bestP - secondP,
            top = top3,
            status = Status.REVIEW,
            source = ""
        )
    }
}
XML
echo "Updated Decision.kt"