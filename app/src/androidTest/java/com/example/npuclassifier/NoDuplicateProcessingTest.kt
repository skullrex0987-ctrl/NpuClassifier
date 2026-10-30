package com.example.npuclassifier

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NoDuplicateProcessingTest {
    @get:Rule
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    @Test
    fun eachProcessedOnce() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(ctx.cacheDir, "duptest").apply { mkdirs() }
        val items = (1..30).map { i ->
            val f = File(dir, "d$i.jpg")
            if (!f.exists()) {
                val bmp = android.graphics.Bitmap.createBitmap(32, 32, android.graphics.Bitmap.Config.ARGB_8888)
                java.io.FileOutputStream(f).use { out -> bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out) }
                bmp.recycle()
            }
            MediaItem(uri = android.net.Uri.fromFile(f), path = f.absolutePath, name = f.name, isVideo = false)
        }
        val inserted = Repo.upsertPending(ctx, items, System.currentTimeMillis()).size
        val cfg = SortSettings(scanMode = "fast", backends = setOf(Backend.CPU))
        ScanController.start(ctx, cfg)
        val t0 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t0 < 120000) {
            if (!ScanController.state.value.running) break
            Thread.sleep(500)
        }
        assertTrue("scan not finished", !ScanController.state.value.running)
        val counts = Repo.counts(ctx)
        // no duplicate rows: total should equal inserted
        assertEquals(inserted, counts.total)
        // all processed (none pending, none running)
        assertEquals(0, counts.pending)
        assertEquals(0, counts.running)
    }
}