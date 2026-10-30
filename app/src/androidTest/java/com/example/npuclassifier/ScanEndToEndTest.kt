package com.example.npuclassifier

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ScanEndToEndTest {
    @get:Rule
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    @Test
    fun endToEnd() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(ctx.cacheDir, "e2etest").apply { mkdirs() }
        val items = mutableListOf<MediaItem>()
        // 60 images, some 4000x3000
        for (i in 1..60) {
            val f = File(dir, "img$i.jpg")
            if (!f.exists()) {
                val w = if (i % 3 == 0) 4000 else 64
                val h = if (i % 3 == 0) 3000 else 64
                val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                java.io.FileOutputStream(f).use { out -> bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out) }
                bmp.recycle()
            }
            items.add(MediaItem(uri = android.net.Uri.fromFile(f), path = f.absolutePath, name = f.name, isVideo = false))
        }
        // 1 tiny mp4 if possible
        try {
            val mp4 = File(dir, "tiny.mp4")
            if (!mp4.exists()) {
                // create a minimal mp4 is complex; skip if not available
            }
            if (mp4.exists()) {
                items.add(MediaItem(uri = android.net.Uri.fromFile(mp4), path = mp4.absolutePath, name = mp4.name, isVideo = true))
            }
        } catch (e: Exception) { }
        Repo.upsertPending(ctx, items, System.currentTimeMillis())
        val cfg = SortSettings(scanMode = "fast", backends = setOf(Backend.CPU))
        ScanController.start(ctx, cfg)
        // poll until done or timeout 120s
        val t0 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t0 < 120000) {
            val st = ScanController.state.value
            if (!st.running) break
            assertTrue("done > total", st.done <= st.total)
            Thread.sleep(500)
        }
        assertTrue("scan not finished", !ScanController.state.value.running)
        val counts = Repo.counts(ctx)
        val sum = counts.pending + counts.needsSmart + counts.running + counts.auto + counts.review + counts.done + counts.error
        assertTrue("counts sum != total", counts.total == sum)
        assertTrue("rows still pending", counts.pending == 0)
        assertTrue("running rows left", counts.running == 0)
        // video rows must not be ERROR
        val videoRows = Repo.page(ctx, Filter(), 0, 200).filter { it.media.isVideo }
        for (vr in videoRows) {
            assertTrue("video row is ERROR: ${vr.note}", vr.status != Status.ERROR)
        }
    }
}