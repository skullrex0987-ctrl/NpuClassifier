package com.example.npuclassifier

import org.junit.Assert.assertEquals
import org.junit.Test

class DecisionPriorityTest {
    @Test
    fun starterOverridesBuiltin() {
        val item = SortItem(
            media = MediaItem(uri = android.net.Uri.parse("file:///dental.jpg"), path = "/dental.jpg", name = "dental.jpg", isVideo = false),
            top = listOf("cat" to 0.9f),
            conf = 0.9f,
            gap = 0.5f,
            ocr = "dental x-ray",
            tags = emptyList()
        )
        val cfg = SortSettings(useBuiltin = true, threshold = 0.5f, minGap = 0.1f)
        val decided = Decision.apply(item, emptyList(), cfg, emptyList())
        assertEquals("Teeth + X-ray", decided.label)
        assertEquals(Status.AUTO, decided.status)
        assertEquals("starter", decided.source)
    }
}