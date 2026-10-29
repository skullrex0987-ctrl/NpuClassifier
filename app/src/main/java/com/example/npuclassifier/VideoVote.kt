package com.example.npuclassifier

object VideoVote {
    fun apply(item: SortItem, cats: List<Category>, cfg: SortSettings, cands: List<Candidate>): SortItem {
        val frames = item.frameItems ?: return item
        if (frames.isEmpty()) return item

        // Count AUTO labels across frames
        val counts = mutableMapOf<String, Int>()
        for (f in frames) {
            if (f.status == Status.AUTO && f.label.isNotEmpty()) {
                counts[f.label] = (counts[f.label] ?: 0) + 1
            }
        }
        if (counts.isEmpty()) {
            return item.copy(status = Status.REVIEW, note = "${frames.size} frames: no confident labels")
        }

        val total = frames.size
        val sorted = counts.entries.sortedByDescending { it.value }
        val winner = sorted[0]
        val share = winner.value.toFloat() / total

        if (share >= cfg.videoMajority) {
            val note = "${frames.size} frames: ${winner.key} ${(share * 100).toInt()}%"
            return item.copy(
                label = winner.key,
                conf = share,
                gap = share - (sorted.getOrElse(1) { Map.Entry("", 0) }.value.toFloat() / total),
                status = Status.AUTO,
                source = frames.firstOrNull { it.label == winner.key }?.source ?: "",
                note = note
            )
        }

        // Review: top 3 with shares
        val top3 = sorted.take(3).map { it.key to (it.value.toFloat() / total) }
        val note = "${frames.size} frames: " + top3.joinToString(", ") { "${it.first} ${(it.second * 100).toInt()}%" }
        return item.copy(
            top = top3,
            status = Status.REVIEW,
            note = note
        )
    }
}