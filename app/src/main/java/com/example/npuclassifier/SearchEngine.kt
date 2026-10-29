package com.example.npuclassifier

import android.content.Context

object SearchEngine {
    fun search(ctx: Context, query: String, cfg: SortSettings, f: Filter): List<SortItem> = emptyList()
    fun indexStatus(ctx: Context, cfg: SortSettings): Pair<Int, Int> = Pair(0, 0)
}