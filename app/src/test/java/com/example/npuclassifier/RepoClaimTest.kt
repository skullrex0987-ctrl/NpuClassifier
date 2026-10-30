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
class RepoClaimTest {
    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    @Test
    fun twoClaimsNeverReturnSameId() {
        val c = ctx()
        val items = listOf(
            MediaItem(uri = android.net.Uri.parse("file:///a.jpg"), path = "/a.jpg", name = "a.jpg", isVideo = false),
            MediaItem(uri = android.net.Uri.parse("file:///b.jpg"), path = "/b.jpg", name = "b.jpg", isVideo = false)
        )
        Repo.upsertPending(c, items, 1L)
        val first = Repo.claim(c, Status.PENDING, 10)
        val second = Repo.claim(c, Status.PENDING, 10)
        val firstIds = first.map { it.id }.toSet()
        val secondIds = second.map { it.id }.toSet()
        assertTrue(firstIds.intersect(secondIds).isEmpty())
    }

    @Test
    fun releaseRunningMakesClaimableAgain() {
        val c = ctx()
        val items = listOf(
            MediaItem(uri = android.net.Uri.parse("file:///c.jpg"), path = "/c.jpg", name = "c.jpg", isVideo = false)
        )
        Repo.upsertPending(c, items, 2L)
        Repo.claim(c, Status.PENDING, 10)
        Repo.releaseRunning(c)
        val again = Repo.claim(c, Status.PENDING, 10)
        assertTrue(again.isNotEmpty())
    }
}