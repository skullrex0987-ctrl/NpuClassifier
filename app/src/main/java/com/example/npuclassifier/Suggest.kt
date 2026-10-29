package com.example.npuclassifier

data class Cluster(val suggestedName: String, val members: List<SortItem>)

object Suggest {
    fun clusters(items: List<SortItem>, minSize: Int = 5, similarity: Float = 0.85f): List<Cluster> {
        val withEmb = items.filter { it.emb != null }
        if (withEmb.isEmpty()) return emptyList()

        val clusters = mutableListOf<MutableList<SortItem>>()
        val centroids = mutableListOf<FloatArray>()

        for (item in withEmb) {
            val emb = item.emb!!
            var placed = false
            for (i in clusters.indices) {
                if (Emb.cosine(emb, centroids[i]) >= similarity) {
                    clusters[i].add(item)
                    // update centroid
                    val c = centroids[i]
                    for (k in c.indices) c[k] = (c[k] * (clusters[i].size - 1) + emb[k]) / clusters[i].size
                    // re-normalize
                    var norm = 0f
                    for (v in c) norm += v * v
                    norm = kotlin.math.sqrt(norm.toDouble()).toFloat()
                    if (norm > 0f) for (k in c.indices) c[k] /= norm
                    placed = true
                    break
                }
            }
            if (!placed) {
                clusters.add(mutableListOf(item))
                centroids.add(emb.copyOf())
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
}