package com.example.npuclassifier

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepoCountsTest {
    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    @Test
    fun sumEqualsTotal() {
        val c = ctx()
        val items = listOf(
            MediaItem(uri = android.net.Uri.parse("file:///x.jpg"), path = "/x.jpg", name = "x.jpg", isVideo = false),
            MediaItem(uri = android.net.Uri.parse("file:///y.jpg"), path = "/y.jpg", name = "y.jpg", isVideo = false)
        )
        Repo.upsertPending(c, items, 3L)
        Repo.claim(c, Status.PENDING, 10)
        val counts = Repo.counts(c)
        val sum = counts.pending + counts.needsSmart + counts.running + counts.auto + counts.review + counts.done + counts.error
        assertEquals(counts.total, sum)
    }
}