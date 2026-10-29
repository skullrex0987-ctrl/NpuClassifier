package com.example.npuclassifier

import android.content.Context

data class Enrichment(val ocr: String, val tags: List<String>)

object Enricher {
    fun run(ctx: Context, m: MediaItem, cfg: SortSettings, wantOcr: Boolean, wantTags: Boolean): Enrichment =
        Enrichment("", emptyList())
}

data class StarterMatch(val label: String, val extra: List<String>)

object StarterRules {
    fun match(name: String, path: String?, ocr: String, tags: List<String>, w: Int, h: Int, smartProbs: Map<String, Float>?): StarterMatch? = null
}