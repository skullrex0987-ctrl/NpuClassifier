package com.example.npuclassifier

import kotlin.math.exp

object Decision {
    fun apply(item: SortItem, cats: List<Category>, cfg: SortSettings, cands: List<Candidate>): SortItem {
        // Video items use VideoVote
        if (item.frameItems != null) {
            return VideoVote.apply(item, cats, cfg, cands)
        }

        if (cfg.model == "clip") {
            return applyClip(item, cats, cfg, cands)
        }
        // MobileNet (Phase 2 behavior)
        val emb = item.emb
        if (cfg.useMine && emb != null && cats.isNotEmpty()) {
            val matches = matchCategoriesMnet(emb, cats)
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
        // StarterRules (screenshots, dental, X-ray, etc.)
        val starter = StarterRules.match(item.media.name, item.media.path, item.ocr, item.tags, 0, 0, null)
        if (starter != null) {
            val label = starter.label + if (starter.extra.isNotEmpty()) " + ${starter.extra.joinToString(",")}" else ""
            return item.copy(label = label, status = Status.AUTO, source = "starter", extraLabels = starter.extra)
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

    fun matchCategoriesMnet(emb: FloatArray, cats: List<Category>): List<Pair<String, Float>> {
        if (cats.isEmpty()) return emptyList()
        val results = mutableListOf<Pair<String, Float>>()
        for (cat in cats) {
            val sims = cat.examples.flatMap { ex ->
                val e = ex.embs["mnet"] ?: ex.embs.values.firstOrNull()
                if (e != null && e.size == emb.size) listOf(Emb.cosine(emb, e)) else emptyList()
            }
            val score = if (sims.isEmpty()) 0f else sims.sortedDescending().take(3).average().toFloat()
            results.add(cat.name to score)
        }
        return results.sortedByDescending { it.second }
    }

    private fun applyClip(item: SortItem, cats: List<Category>, cfg: SortSettings, cands: List<Candidate>): SortItem {
        val emb = item.emb ?: return item.copy(status = Status.REVIEW, source = "")

        // a) Use my categories (taught examples with clip embeddings)
        if (cfg.useMine) {
            val catsWithExamples = cats.filter { it.examples.isNotEmpty() }
            if (catsWithExamples.isNotEmpty()) {
                val matches = matchCategoriesClip(emb, catsWithExamples)
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

        // b) StarterRules (screenshots, dental, X-ray)
        val starter = StarterRules.match(item.media.name, item.media.path, item.ocr, item.tags, 0, 0, null)
        if (starter != null) {
            val label = starter.label + if (starter.extra.isNotEmpty()) " + ${starter.extra.joinToString(",")}" else ""
            return item.copy(label = label, status = Status.AUTO, source = "starter", extraLabels = starter.extra)
        }

        // c) TEXT RULE: zero-shot using candidates
        val filteredCands = cands.filter { c ->
            (c.source == "mine" && cfg.useMine) || (c.source == "builtin" && cfg.useBuiltin)
        }
        if (filteredCands.isEmpty()) {
            return item.copy(status = Status.REVIEW, source = "")
        }

        val sims = filteredCands.map { c -> c.name to Emb.cosine(emb, c.textEmb) }
        val scaled = sims.map { 100f * it.second }
        val maxScaled = scaled.maxOrNull() ?: 0f
        val exps = scaled.map { exp((it - maxScaled).toDouble()).toFloat() }
        val sumExps = exps.sum()
        val probs = exps.map { it / sumExps }

        val candidateList = sims.mapIndexed { i, (name, _) ->
            Triple(name, filteredCands[i].source, probs[i])
        }.sortedByDescending { it.third }

        val top3 = candidateList.take(3).map { it.first to it.third }

        val best = candidateList[0]
        val second = candidateList.getOrElse(1) { Triple("", "builtin", 0f) }

        if (best.third >= cfg.threshold && (best.third - second.third) >= cfg.minGap) {
            return item.copy(
                label = best.first,
                conf = best.third,
                gap = best.third - second.third,
                top = top3,
                status = Status.AUTO,
                source = best.second
            )
        }

        return item.copy(
            conf = best.third,
            gap = best.third - second.third,
            top = top3,
            status = Status.REVIEW,
            source = ""
        )
    }

    private fun matchCategoriesClip(emb: FloatArray, cats: List<Category>): List<Pair<String, Float>> {
        val results = mutableListOf<Pair<String, Float>>()
        for (cat in cats) {
            val sims = cat.examples.flatMap { ex ->
                val e = ex.embs["clip"] ?: ex.embs.values.firstOrNull()
                if (e != null && e.size == emb.size) listOf(Emb.cosine(emb, e)) else emptyList()
            }
            val score = if (sims.isEmpty()) 0f else sims.sortedDescending().take(3).average().toFloat()
            results.add(cat.name to score)
        }
        return results.sortedByDescending { it.second }
    }
}