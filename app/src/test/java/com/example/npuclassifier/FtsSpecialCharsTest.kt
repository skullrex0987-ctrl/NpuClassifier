package com.example.npuclassifier

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FtsSpecialCharsTest {
    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    @Test
    fun specialCharsDoNotCrash() {
        val c = ctx()
        val items = listOf(
            MediaItem(uri = android.net.Uri.parse("file:///s.jpg"), path = "/s.jpg", name = "s.jpg", isVideo = false)
        )
        Repo.upsertPending(c, items, 5L)
        // insert ocr with special chars via saveEnrichment
        val id = Repo.counts(c).total.toLong() // rough; use page
        val page = Repo.page(c, com.example.npuclassifier.Filter(), 0, 10)
        if (page.isNotEmpty()) {
            Repo.saveEnrichment(c, page[0].media.id, "hello \"world\" * test - foo", emptyList())
        }
        // should not crash
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
        val page = Repo.page(c, com.example.npuclassifier.Filter(), 0, 10)
        if (page.isNotEmpty()) {
            Repo.saveEnrichment(c, page[0].media.id, "payment upi transaction", emptyList())
        }
        val r = Repo.ftsSearch(c, "payment", 10)
        assertTrue(r.isNotEmpty())
    }
}