package com.example.npuclassifier

data class Cluster(val suggestedName: String, val members: List<SortItem>)

object Suggest {
    fun clusters(items: List<SortItem>, minSize: Int = 5, similarity: Float = 0.85f): List<Cluster> {
        val withEmb = items.filter { it.emb != null }
        if (withEmb.isEmpty()) return emptyList()

        val clusters = mutableListOf<MutableList<SortItem>>()
        // Raw sum per cluster; centroid = normalize(sum) == normalized average.
        val sums = mutableListOf<FloatArray>()

        for (item in withEmb) {
            val emb = item.emb!!
            var placed = false
            for (i in clusters.indices) {
                if (sums[i].size != emb.size) continue
                if (Emb.cosine(emb, normalize(sums[i])) >= similarity) {
                    clusters[i].add(item)
                    for (k in sums[i].indices) sums[i][k] += emb[k]
                    placed = true
                    break
                }
            }
            if (!placed) {
                clusters.add(mutableListOf(item))
                sums.add(emb.copyOf())
            }
        }

        return clusters
            .filter { it.size >= minSize }
            .sortedByDescending { it.size }
            .take(12)
            .map { members ->
                val names = members.map { SortEngine.prettyLabel(it.top.getOrElse(0) { "" to 0f }.first) }
                val suggestedName = names.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: "Cluster"
                Cluster(suggestedName, members)
            }
    }

    private fun normalize(v: FloatArray): FloatArray {
        var norm = 0f
        for (x in v) norm += x * x
        norm = kotlin.math.sqrt(norm.toDouble()).toFloat()
        if (norm == 0f) return v
        return FloatArray(v.size) { v[it] / norm }
    }
}