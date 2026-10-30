package com.example.npuclassifier

object SearchUtil {
    fun rrfMerge(fts: List<Long>, semantic: List<Long>, k: Int = 60): List<Long> {
        val scores = mutableMapOf<Long, Float>()
        fts.forEachIndexed { i, id -> scores[id] = (scores[id] ?: 0f) + 1f / (k + i + 1) }
        semantic.forEachIndexed { i, id -> scores[id] = (scores[id] ?: 0f) + 1f / (k + i + 1) }
        return scores.entries.sortedByDescending { it.value }.map { it.key }
    }
}