package com.example.npuclassifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RrfMergeTest {
    @Test
    fun mergeCombines() {
        val fts = listOf(1L, 2L, 3L)
        val sem = listOf(3L, 4L, 5L)
        val merged = SearchUtil.rrfMerge(fts, sem)
        // 3 appears in both → should rank high
        assertTrue(merged.indexOf(3L) <= 1)
        assertEquals(5, merged.size)
    }
    @Test
    fun emptyInputs() {
        assertEquals(emptyList<Long>(), SearchUtil.rrfMerge(emptyList(), emptyList()))
    }
}