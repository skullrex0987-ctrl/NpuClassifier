package com.example.npuclassifier

object Decision {
    fun matchCategories(emb: FloatArray, cats: List<Category>): List<Pair<String, Float>> {
        if (cats.isEmpty()) return emptyList()
        val results = mutableListOf<Pair<String, Float>>()
        for (cat in cats) {
            // Examples embedded by a different model can have another dimension; skip them rather
            // than letting cosine() run off the end of the shorter vector.
            val sims = cat.examples.filter { it.emb.size == emb.size }.map { Emb.cosine(emb, it.emb) }
            val score = if (sims.isEmpty()) 0f else sims.sortedDescending().take(3).average().toFloat()
            results.add(cat.name to score)
        }
        return results.sortedByDescending { it.second }
    }

    fun apply(item: SortItem, cats: List<Category>, cfg: SortSettings): SortItem {
        val emb = item.emb
        if (cfg.useMine && emb != null && cats.isNotEmpty()) {
            val matches = matchCategories(emb, cats)
            if (matches.isNotEmpty()) {
                val best = matches[0]
                val second = matches.getOrElse(1) { "" to 0f }
                if (best.second >= cfg.catThreshold && (best.second - second.second) >= cfg.catGap) {
                    // NOTE: conf/gap are deliberately left untouched. They describe the ImageNet
                    // rule and apply() is re-run on already-decided items (redecide), so overwriting
                    // them here would leak a category score into the builtin branch next pass.
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
}