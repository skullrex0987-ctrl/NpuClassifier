package com.example.npuclassifier

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FtsSpecialCharsTest {
    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    @Test
    fun specialCharsDoNotCrash() {
        val c = ctx()
        val items = listOf(
            MediaItem(uri = android.net.Uri.parse("file:///s.jpg"), path = "/s.jpg", name = "s.jpg", isVideo = false)
        )
        Repo.upsertPending(c, items, 5L)
        val page = Repo.page(c, Filter(), 0, 10)
        if (page.isNotEmpty()) {
            Repo.saveEnrichment(c, page[0].media.id, "hello \"world\" * test - foo", emptyList())
        }
        val r1 = Repo.ftsSearch(c, "hello", 10)
        val r2 = Repo.ftsSearch(c, "\"", 10)
        val r3 = Repo.ftsSearch(c, "*", 10)
        val r4 = Repo.ftsSearch(c, "-", 10)
        assertTrue(true)
    }

    @Test
    fun prefixQuery() {
        val c = ctx()
        val items = listOf(
            MediaItem(uri = android.net.Uri.parse("file:///p.jpg"), path = "/p.jpg", name = "p.jpg", isVideo = false)
        )
        Repo.upsertPending(c, items, 6L)
        val page = Repo.page(c, Filter(), 0, 10)
        assertTrue("page empty", page.isNotEmpty())
        if (page.isNotEmpty()) {
            Repo.saveEnrichment(c, page[0].media.id, "payment upi transaction", emptyList())
        }
        val r = Repo.ftsSearch(c, "pay", 10)
        assertTrue("search empty, r=$r", r.isNotEmpty())
    }
}